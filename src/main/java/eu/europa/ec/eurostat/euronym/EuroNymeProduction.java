/**
 * 
 */
package eu.europa.ec.eurostat.euronym;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.geotools.filter.text.cql2.CQL;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
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

	private static String basePath = "/home/juju/Bureau/";
	private static String namesStruct = basePath + "gisco/tmp/namesStruct.gpkg";

	//TODO check / remove duplicates
	//TODO correct paris position

	//TODO use stat atlas - for multi-ling ? https://ec.europa.eu/statistical-atlas/arcgis/rest/services/Basemaps/StatAtlas_Cities_Labels_2014/MapServer/0/query?where=POPL_SIZE%3E50000&outSR=3035&inSR=3035&geometry=3428439.0697888224,2356253.0645389506,4339693.4974049805,2548197.243346825&geometryType=esriGeometryEnvelope&f=json&outFields=STTL_NAME,POPL_SIZE
	//TODO add other aggregates: EFTA, UE, etc.
	//TODO improve coverage for CH, RO, etc. Why is Vaduz missing?
	//TODO check euro gazeeter.
	//TODO elaborate: different font size, weight, etc. depending on population



	public static void main(String[] args) throws Exception {
		System.out.println("Start");

		//
		structure();


		//get country codes
		HashSet<String> ccs = new HashSet<>();
		ccs.addAll(FeatureUtil.getIdValues(GeoData.getFeatures(namesStruct), "cc"));
		ccs.add("EUR");
		//TODO ccs.add("EU");
		//TODO ccs.add("EFTA");

		//ccs.add("FR");


		//generate
		for(String cc : ccs) {
			for (int lod : new int[] { 20, 50, 100, 200 }) {
				System.out.println("******* " + cc + " LOD " + lod);

				// get input labels
				Filter f = cc.equals("EUR")? null : CQL.toFilter("cc = '"+cc+"'");
				ArrayList<Feature> fs = GeoData.getFeatures(namesStruct, null, f);
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
				for (Feature f_ : fs)
					f_.getAttributes().remove("cc");

				// save
				//System.out.println("save as GPKG");
				//GeoData.save(fs, basePath + "euronymes.gpkg", CRSUtil.getETRS89_LAEA_CRS());
				System.out.println("save as CSV");
				new File("./pub/v1/"+lod).mkdirs();
				CSVUtil.save(CSVUtil.featuresToCSV(fs), "./pub/v1/"+lod+"/"+cc+".csv");
			}
		}

		System.out.println("End");
	}



	private static void setR1(ArrayList<Feature> fs, int resMin, double zf, int radPix) {

		//make index
		STRtree index = FeatureUtil.getIndexSTRtree(fs);

		//go through all features
		for(Feature f : fs) {

			//get rs
			int rs = (int) f.getAttribute("rs");
			//get pop
			int pop = Integer.parseInt(f.getAttribute("pop").toString());

			//initialise r1 with maximum importance
			f.setAttribute("r1", rs);

			for (int res = resMin; res <= rs; res *= zf) {

				//get the ones around
				Envelope env = (Envelope) f.getGeometry().getEnvelopeInternal();
				Envelope searchEnv = new Envelope(env);
				searchEnv.expandBy(radPix * res, radPix * res);
				List<Feature> neigh = index.query(searchEnv);

				//check if one more important exists around
				boolean moreImportantExists = false;
				for(Feature f_ : neigh) {
					int pop_ = Integer.parseInt(f_.getAttribute("pop").toString());
					if(pop_ <= pop) continue;
					moreImportantExists = true;
					break;
				}

				//keep looking more important to the next res level
				if(! moreImportantExists)
					continue;

				//set r1 and break
				f.setAttribute("r1", res);
				break;
			}
		}

	}



	/**
	 * @param fs       The labels
	 * @param fontSize The label font size
	 * @param resMin   The minimum resolution (in m/pixel). The unnecessary labels below will be removed.
	 * @param resMax   The maximum resolution (in m/pixel)
	 * @param zf       The zoom factor, between resolutions. For example: 1.2
	 * @param pixX     The buffer zone without labels around - X direction
	 * @param pixY     The buffer zone without labels around - Y direction
	 */
	private static ArrayList<Feature> generate(ArrayList<Feature> fs, int fontSize, int resMin, int resMax, double zf, int pixX, int pixY) {

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

	private static void structure() {

		// the output
		Collection<Feature> out = new ArrayList<>();

		// Add ERM BuiltupP

		System.out.println("ERM - BuiltupP");
		String erm = basePath + "gisco/geodata/euro-regional-map-gpkg/data/OpenEuroRegionalMap.gpkg";
		ArrayList<Feature> buP = GeoData.getFeatures(erm, "BuiltupP", "id");
		System.out.println(buP.size() + " features loaded");
		CoordinateReferenceSystem crsERM = GeoData.getCRS(erm);

		for (Feature f : buP) {
			Feature f_ = new Feature();

			// name
			// NAMA1 NAMA2 NAMN1 NAMN2
			String name = (String) f.getAttribute("NAMA1");
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
			if(name.contains("Arrondissement"))
				name = name.replace(" Arrondissement", "");
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

			//country code
			f_.setAttribute("cc", alterCountryCode(f.getAttribute("ICC").toString()));

			out.add(f_);
		}


		// REGIO town names

		System.out.println("REGIO - town names");
		String nt_ = basePath + "gisco/geodata/regio_town_names/nt.gpkg";
		ArrayList<Feature> nt = GeoData.getFeatures(nt_, "STTL_ID");
		System.out.println(nt.size() + " features loaded");
		CoordinateReferenceSystem crsNT = GeoData.getCRS(nt_);

		for (Feature f : nt) {
			Feature f_ = new Feature();

			// name
			String name = (String) f.getAttribute("STTL_NAME");
			if (name.length() == 0)
				continue;
			if(name.contains(" / "))
				continue;

			//correction
			if(name.equals("Cize")) name = "Champagnole";

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

			//country code
			f_.setAttribute("cc", alterCountryCode(f.getAttribute("CNTR_CODE").toString()));

			out.add(f_);
		}


		//manual corrections
		for(Feature f : out) {
			String name = f.getAttribute("name").toString();

			if(name.equals("Valletta (greater)")) f.setAttribute("name", "Valletta");
			if(name.equals("Greater City of Athens")) f.setAttribute("name", "Athens");
			if(name.equals("Greater City of Thessaloniki")) f.setAttribute("name", "Thessaloniki");
			if(name.equals("Greater Manchester")) f.setAttribute("name", "Manchester");
			if(name.equals("Greater Nottingham")) f.setAttribute("name", "Nottingham");
			//if(name.equals("Alacant/Alicante")) f.setAttribute("name", "Alicante");
			//if(name.equals("Alicante/Alacant")) f.setAttribute("name", "Alicante");
			//if(name.equals("Gijon/Xixon")) f.setAttribute("name", "Gijon");
			if(name.equals("Tyneside conurbation")) f.setAttribute("name", "Tyneside");

			if(name.equals("Brussel")) f.setAttribute("pop", 210000);

			//if(name.contains("Metropoli"))
			//	System.out.println(name + " " + f.getAttribute("pop"));
		}


		// save output
		System.out.println("Save " + out.size());
		GeoData.save(out, namesStruct, CRSUtil.getETRS89_LAEA_CRS());
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
		case "EL": return "GR";
		case "GB": return "UK";
		case "ND": return "UK";
		case "IM": return "UK";
		//case "SJ": return "NO";
		//case "FO": return "DK";
		default: return in;
		}
	}


	/*
	 * /make agent toponymes ArrayList<AgentToponyme> agents = new ArrayList<>();
	 * for(Feature f :fs) agents.add(new AgentToponyme(f)); //make engine
	 * Engine<AgentToponyme> e = new Engine<>(agents); //start e.activateQueue();
	 */

}
