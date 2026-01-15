/**
 * 
 */
package eu.europa.ec.eurostat.euronym;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.geotools.filter.text.cql2.CQL;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import eu.europa.ec.eurostat.jgiscotools.feature.Feature;
import eu.europa.ec.eurostat.jgiscotools.feature.FeatureUtil;
import eu.europa.ec.eurostat.jgiscotools.io.CSVUtil;
import eu.europa.ec.eurostat.jgiscotools.io.geo.CRSUtil;
import eu.europa.ec.eurostat.jgiscotools.io.geo.GeoData;
import eu.europa.ec.eurostat.jgiscotools.util.Util;

/**
 * @author julien Gaffuri
 *
 */
public class EuroNymeProduction {

	private static String basePath = "/home/juju/geodata/";
	private static String version = "v3";

	// set to true to use regio names only to necessary countries
	private static boolean limitUseRegio = true;

	// hard validation on 1:1M

	// TODO local names, with accents - see in ERM (NAMN). OR: eurogazeeter ?
	// TODO:check EBM_NAM PPL NAMA NAMN

	// TODO add other aggregates: EFTA, UE, etc.
	// TODO elaborate: different font size, weight, etc. depending on population

	// OR:
	// TODO use stat atlas - for multi-ling ?
	// https://ec.europa.eu/statistical-atlas/arcgis/rest/services/Basemaps/StatAtlas_Cities_Labels_2014/MapServer/0
	// https://ec.europa.eu/statistical-atlas/arcgis/rest/services/Basemaps/StatAtlas_Cities_Labels_2014/MapServer/0/query?where=POPL_SIZE%3E50000&outSR=3035&inSR=3035&geometry=3428439.0697888224,2356253.0645389506,4339693.4974049805,2548197.243346825&geometryType=esriGeometryEnvelope&f=json&outFields=STTL_NAME,POPL_SIZE
	// https://ec.europa.eu/statistical-atlas/arcgis/rest/services/Basemaps/StatAtlas_Cities_Labels_2014/MapServer/0/query?where=POPL_SIZE%3E0&f=json
	// &f=json
	// https://ec.europa.eu/statistical-atlas/arcgis/rest/services/Basemaps/StatAtlas_Cities_Labels_2014/MapServer

	public static void main(String[] args) throws Exception {
		System.out.println("Start");

		// prepare data from inputs
		// format: name,pop,cc,lon,lat
		prepareDataFromInput("tmp/namesStruct_ASCII.gpkg", true, false);
		prepareDataFromInput("tmp/namesStruct_UTF.gpkg", false, false);
		prepareDataFromInput("tmp/namesStruct_UTF_LATIN.gpkg", false, true);
		if (true) return;

		// get country codes
		HashSet<String> ccs = new HashSet<>();
		ccs.addAll(FeatureUtil.getIdValues(GeoData.getFeatures("tmp/namesStruct_ASCII.gpkg"), "cc"));
		ccs.add("EUR");
		// TODO ccs.add("EU");
		// TODO ccs.add("EFTA");
		// ccs.add("FR");

		// generate
		for (String cc : ccs) {
			for (int lod : new int[] { 20, 50, 100, 200 }) {
				for (String enc : new String[] { "UTF", "UTF_LATIN", "ASCII" }) {
					System.out.println("******* " + cc + " LOD " + lod + " enc=" + enc);

					// get input labels
					Filter f = cc.equals("EUR") ? null : CQL.toFilter("cc = '" + cc + "'");
					ArrayList<Feature> fs = GeoData.getFeatures("tmp/namesStruct_" + enc + ".gpkg",
							null, f);
					System.out.println(fs.size() + " labels loaded");

					// do
					int marginPix = 40;
					fs = generate(fs, 14, lod, 100000, 1.2, marginPix, marginPix);
					System.out.println(fs.size());

					// refine with r1 setting
					int radR1Pix = 150;
					setR1(fs, lod, 1.2, radR1Pix);

					// clean attributes
					for (Feature f_ : fs)
						f_.getAttributes().remove("gl");
					for (Feature f_ : fs)
						f_.getAttributes().remove("pop");
					if (!cc.equals("EUR"))
						for (Feature f_ : fs)
							f_.getAttributes().remove("cc");

					// save
					// System.out.println("save as GPKG");
					// GeoData.save(fs, basePath + "euronymes.gpkg", CRSUtil.getETRS89_LAEA_CRS());
					System.out.println("save as CSV");
					new File("./pub/" + version + "/" + enc + "/" + lod).mkdirs();
					CSVUtil.save(CSVUtil.featuresToCSV(fs),
							"./pub/" + version + "/" + enc + "/" + lod + "/" + cc + ".csv");
				}
			}
		}

		System.out.println("End");
	}

	private static void setR1(ArrayList<Feature> fs, int resMin, double zf, int radPix) {

		// make index
		STRtree index = FeatureUtil.getIndexSTRtree(fs);

		// go through all features
		for (Feature f : fs) {

			// get rs
			int rs = (int) f.getAttribute("rs");
			// get pop
			int pop = Integer.parseInt(f.getAttribute("pop").toString());

			// initialise r1 with maximum importance
			f.setAttribute("r1", rs);

			for (int res = resMin; res <= rs; res *= zf) {

				// get the ones around
				Envelope env = (Envelope) f.getGeometry().getEnvelopeInternal();
				Envelope searchEnv = new Envelope(env);
				searchEnv.expandBy(radPix * res, radPix * res);
				List<Feature> neigh = index.query(searchEnv);

				// check if one more important exists around
				boolean moreImportantExists = false;
				for (Feature f_ : neigh) {
					int pop_ = Integer.parseInt(f_.getAttribute("pop").toString());
					if (pop_ <= pop)
						continue;
					moreImportantExists = true;
					break;
				}

				// keep looking more important to the next res level
				if (!moreImportantExists)
					continue;

				// set r1 and break
				f.setAttribute("r1", res);
				break;
			}
		}

	}

	/**
	 * @param fs       The labels
	 * @param fontSize The label font size
	 * @param resMin   The minimum resolution (in m/pixel). The unnecessary labels
	 *                 below will be removed.
	 * @param resMax   The maximum resolution (in m/pixel)
	 * @param zf       The zoom factor, between resolutions. For example: 1.2
	 * @param pixX     The buffer zone without labels around - X direction
	 * @param pixY     The buffer zone without labels around - Y direction
	 */
	private static ArrayList<Feature> generate(ArrayList<Feature> fs, int fontSize, int resMin, int resMax, double zf,
			int pixX, int pixY) {

		// initialise rs
		for (Feature f : fs)
			f.setAttribute("rs", resMax);

		for (int res = resMin; res <= resMax; res *= zf) {
			System.out.println("Resolution: " + res);

			// extract only the labels that are visible for this resolution
			final int res_ = res;
			List<Feature> fs_ = fs.stream().filter(f -> (Integer) f.getAttribute("rs") > res_)
					.collect(Collectors.toList());
			System.out.println("   nb = " + fs_.size());

			// compute label envelopes
			for (Feature f : fs_)
				f.setAttribute("gl", getLabelEnvelope(f, fontSize, res));

			// make spatial index, with only the ones remaining as visible for res
			Quadtree index = new Quadtree();
			for (Feature f : fs_)
				index.insert((Envelope) f.getAttribute("gl"), f);

			// analyse labels one by one
			for (Feature f : fs_) {
				// System.out.println("----");

				Integer rs = (Integer) f.getAttribute("rs");
				if (rs <= res)
					continue;

				// get envelope, enlarged
				Envelope env = (Envelope) f.getAttribute("gl");
				Envelope searchEnv = new Envelope(env);
				searchEnv.expandBy(pixX * res, pixY * res);

				// get other labels overlapping/nearby with index
				List<Feature> neigh = index.query(searchEnv);
				// refine list of neighboors: keep ony the ones intersecting
				Predicate<Feature> pr2 = f2 -> searchEnv.intersects((Envelope) f2.getAttribute("gl"));
				;
				neigh = neigh.stream().filter(pr2).collect(Collectors.toList());

				// in case no neighboor...
				if (neigh.size() == 1)
					continue;

				// get best label to keep
				Feature toKeep = getBestLabelToKeep(neigh);

				// set rs of others, and remove them from index
				neigh.remove(toKeep);
				for (Feature f_ : neigh) {
					f_.setAttribute("rs", res);
					index.remove((Envelope) f_.getAttribute("gl"), f_);
				}

			}

		}

		// filter - keep only few
		return (ArrayList<Feature>) fs.stream().filter(f -> (Integer) f.getAttribute("rs") > resMin)
				.collect(Collectors.toList());
	}

	private static Feature getBestLabelToKeep(List<Feature> fs) {
		// get the one with:
		// 1. the largest population
		Feature fBest = null;
		int popMax = -1;
		for (Feature f : fs) {
			int pop = Integer.parseInt(f.getAttribute("pop").toString());
			if (pop <= popMax)
				continue;
			popMax = pop;
			fBest = f;
		}
		// 2. the shorter
		// TODO

		return fBest;
	}

	private static void prepareDataFromInput(String outFileName, boolean ascii, boolean forceLatin) {

		// the output data
		Collection<Feature> out = new ArrayList<>();

		// Add ERM BuiltupP

		System.out.println("ERM - BuiltupP");
		String erm = basePath + "eurogeographics/ERM/data/FullEurope/OpenEuroRegionalMap.gpkg";
		ArrayList<Feature> buP = GeoData.getFeatures(erm, "BuiltupP", "id");
		System.out.println(buP.size() + " features loaded");
		CoordinateReferenceSystem crsERM = GeoData.getCRS(erm);

		for (Feature f : buP) {
			Feature f_ = new Feature();

			String icc = (String) f.getAttribute("ICC");

			// name
			// NAMA: ASCII character - NAMN: utf8
			// NAMA1 NAMA2 NAMN1 NAMN2

			String name = null;
			if (ascii || forceLatin && (icc.equals("GR") || icc.equals("CY") || icc.equals("UA") || icc.equals("MK")
					|| icc.equals("GE"))) {
				name = (String) f.getAttribute("NAMA1");
				if (name == null || name.equals("UNK")) {
					System.out.println("No NAMA1 for " + f.getID() + " " + f.getAttribute("ICC"));
					name = (String) f.getAttribute("NAMA2");
					if (name == null || name.equals("UNK")) {
						System.out.println("No NAMA2 for " + f.getID() + " " + f.getAttribute("ICC"));
						name = (String) f.getAttribute("NAMN1");
						if (name == null || name.equals("UNK")) {
							System.out.println("No NAMN1 for " + f.getID() + " " + f.getAttribute("ICC"));
							name = (String) f.getAttribute("NAMN2");
							if (name == null || name.equals("UNK")) {
								System.err.println("No NAMN2 for " + f.getID() + " " + f.getAttribute("ICC"));
								continue;
							}
						}
					}
				}
			} else {
				name = (String) f.getAttribute("NAMN1");
				if (name == null || name.equals("UNK")) {
					System.out.println("No NAMN1 for " + f.getID() + " " + f.getAttribute("ICC"));
					name = (String) f.getAttribute("NAMN2");
					if (name == null || name.equals("UNK")) {
						System.out.println("No NAMN2 for " + f.getID() + " " + f.getAttribute("ICC"));
						name = (String) f.getAttribute("NAMA1");
						if (name == null || name.equals("UNK")) {
							System.out.println("No NAMA1 for " + f.getID() + " " + f.getAttribute("ICC"));
							name = (String) f.getAttribute("NAMA2");
							if (name == null || name.equals("UNK")) {
								System.err.println("No NAMA2 for " + f.getID() + " " + f.getAttribute("ICC"));
								continue;
							}
						}
					}
				}
			}
			f_.setAttribute("name", name);

			// lon / lat
			Point g = (Point) f.getGeometry();
			f_.setAttribute("lon", Double.toString(Util.round(g.getCoordinate().x, 3)));
			f_.setAttribute("lat", Double.toString(Util.round(g.getCoordinate().y, 3)));

			// geometry
			// project
			f_.setGeometry(CRSUtil.toLAEA(f.getGeometry(), crsERM));
			for (Coordinate c : f_.getGeometry().getCoordinates()) {
				double z = c.x;
				c.x = c.y;
				c.y = z;
			}

			// population
			// PPL PP1 PP2
			Integer pop = (Integer) f.getAttribute("PPL");
			if (pop < 0 || pop == null) {
				Integer pop1 = (Integer) f.getAttribute("PP1");
				Integer pop2 = (Integer) f.getAttribute("PP2");
				if (pop1 >= 0 && pop2 >= 0) {
					pop = pop1 + (pop2 - pop1) / 3;
				} else if (pop1 < 0 && pop2 >= 0) {
					// System.out.println("pop2 " + pop2+name + " "+pop1);
					pop = pop2 / 2;
				} else if (pop1 >= 0 && pop2 < 0) {
					// System.out.println("pop1 " + pop1+name + " "+pop2);
					pop = pop1 * 2;
				} else if (pop1 < 0 && pop2 < 0) {
					// System.out.println(pop1+" "+pop2);
					// do something here
					pop = 0;
				}
			}
			f_.setAttribute("pop", pop.toString());

			// country code
			f_.setAttribute("cc", alterCountryCode(f.getAttribute("ICC").toString()));

			out.add(f_);
		}

		// REGIO town names

		System.out.println("REGIO - town names");
		String nt_ = basePath + "regio_town_names/centroides_wgs84.gpkg";
		ArrayList<Feature> nt = GeoData.getFeatures(nt_, "STTL_ID");
		System.out.println(nt.size() + " features loaded");
		CoordinateReferenceSystem crsNT = GeoData.getCRS(nt_);

		List<String> cntsRegio = Arrays.asList(new String[] { "RO", "BA", "AL", "ME", "HU", "RS", "BG", "XK" });
		for (Feature f : nt) {
			Feature f_ = new Feature();

			String cc = f.getAttribute("CNTR_CODE").toString();
			if (limitUseRegio && !cntsRegio.contains(cc))
				continue;

			// name
			String name = (String) f.getAttribute("STTL_NAME");
			if (name.length() == 0)
				continue;
			if (name.contains(" / "))
				continue;
			f_.setAttribute("name", name);

			// lon / lat
			Point g = f.getGeometry().getCentroid();
			f_.setAttribute("lon", Double.toString(Util.round(g.getCoordinate().x, 3)));
			f_.setAttribute("lat", Double.toString(Util.round(g.getCoordinate().y, 3)));

			// geometry
			// project
			f_.setGeometry(CRSUtil.toLAEA(g, crsNT));
			for (Coordinate c : f_.getGeometry().getCoordinates()) {
				double z = c.x;
				c.x = c.y;
				c.y = z;
			}

			// population
			Integer pop = (int) Double.parseDouble(f.getAttribute("POPL_2011").toString());
			f_.setAttribute("pop", pop.toString());

			// country code
			f_.setAttribute("cc", alterCountryCode(f.getAttribute("CNTR_CODE").toString()));

			out.add(f_);
		}

		// manual corrections
		for (Feature f : out) {
			String name = f.getAttribute("name").toString();

			// romanian case
			if (name.contains("Municipiul"))
				f.setAttribute("name", name.replace("Municipiul ", ""));
			if (name.contains("Oraş"))
				f.setAttribute("name", name.replace("Oraş ", ""));

			if (name.equals("Arcachon"))
				f.setAttribute("pop", 30000); // 11630);
			if (name.equals("Brussel")) {
				f.setAttribute("pop", 1200000); // 100000);
				f.setAttribute("name", "Bruxelles/Brussel");
			}
			if (name.equals("Vaduz"))
				f.setAttribute("pop", 12000); // 5300);
			if (name.equals("'s-Gravenhage"))
				f.setAttribute("name", "Den Haag");
			if (name.equals("Petroșani-Colonie"))
				f.setAttribute("name", "Petroșani");

			if (name.equals("Potsdam"))
				f.setAttribute("pop", 300000);


			if (name.equals("Paris")) System.out.println(name);
			if (name.equals("Marseille")) System.out.println(name);
			if (name.equals("Lyon")) System.out.println(name);


			// deal with "arrondissement"
			if (name.equals("Paris"))
				f.setAttribute("pop", 10000000);
			if (name.contains("Arrondissement") && name.contains("Paris"))
				f.setAttribute("pop", 100000);

			if (name.contains("Arrondissement") && name.contains("Marseille"))
				f.setAttribute("pop", 50000);
			if (name.equals("Marseille 1er Arrondissement")) {
				f.setAttribute("pop", 820000);
				f.setAttribute("name", "Marseille");
			}
			if (name.contains("Arrondissement") && name.contains("Lyon"))
				f.setAttribute("pop", 40000);
			if (name.equals("Lyon 1er Arrondissement")) {
				f.setAttribute("pop", 600000);
				f.setAttribute("name", "Lyon");
			}
		}

		// manual additions for few missing ones
		addFeature(out, "Vila do Porto", "PT", 5552, -25.14535, 36.95069);
		addFeature(out, "Angra do Heroísmo", "PT", 6480, -27.2114, 38.6447);
		addFeature(out, "Praia da Vitória", "PT", 5331, -27.0539, 38.7339);
		addFeature(out, "Capelas", "PT", 7191, -25.68776, 37.83288);
		addFeature(out, "Ponta Delgada", "PT", 29526, -25.66484, 37.74029);
		addFeature(out, "Rabo de Peixe", "PT", 5496, -25.5791, 37.8162);
		addFeature(out, "Lagoa", "PT", 5203, -25.5798, 37.7469);
		addFeature(out, "Ribeira Grande", "PT", 7872, -25.5231, 37.8217);
		addFeature(out, "Vila Franca do Campo", "PT", 7175, -25.4357, 37.7174);

		// save output
		System.out.println("Save " + out.size());
		GeoData.save(out, outFileName, CRSUtil.getETRS89_LAEA_CRS());
	}

	private static void addFeature(Collection<Feature> out, String name, String cc,
			int pop, double lon, double lat) {
		Feature f = new Feature();
		f.setAttribute("name", name);
		f.setAttribute("cc", cc);
		f.setAttribute("pop", pop);
		f.setAttribute("lon", lon);
		f.setAttribute("lat", lat);
		Point geom = new GeometryFactory().createPoint(new Coordinate(lat, lon));
		geom = (Point) CRSUtil.toLAEA(geom, CRSUtil.getWGS_84_CRS());
		geom = new GeometryFactory().createPoint(new Coordinate(geom.getY(), geom.getX()));
		f.setGeometry(geom);
		out.add(f);
	}

	/*
	 * private static ArrayList<Feature> getNameExtend(double pixSize, int fontSize)
	 * { ArrayList<Feature> fs = GeoData.getFeatures(namesStruct); for (Feature f :
	 * fs) { Envelope env = getLabelEnvelope(f, fontSize, pixSize);
	 * f.setGeometry(JTSGeomUtil.getGeometry(env)); } return fs; }
	 */

	/**
	 * @param f        The label object.
	 * @param fontSize The font size to apply, in pts.
	 * @param pixSize  The zoom level: size of a pixel in m.
	 * @return
	 */
	private static Envelope getLabelEnvelope(Feature f, int fontSize, double pixSize) {
		Coordinate c = f.getGeometry().getCoordinate();
		double x = c.x;
		double y = c.y;

		// 12pt = 16px
		double h = pixSize * fontSize * 1.333333;
		double widthFactor = 0.5;
		double w = widthFactor * h * ((String) f.getAttribute("name")).length();

		return new Envelope(x, x + w, y, y + h);
	}

	private static String alterCountryCode(String in) {
		switch (in) {
			case "EL":
				return "GR";
			case "GB":
				return "UK";
			case "ND":
				return "UK";
			case "IM":
				return "UK";
			// case "SJ": return "NO";
			// case "FO": return "DK";
			default:
				return in;
		}
	}

	/*
	 * /make agent toponymes ArrayList<AgentToponyme> agents = new ArrayList<>();
	 * for(Feature f :fs) agents.add(new AgentToponyme(f)); //make engine
	 * Engine<AgentToponyme> e = new Engine<>(agents); //start e.activateQueue();
	 */

}
