/**
 * 
 */
package eu.europa.ec.eurostat.euronym;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import eu.europa.ec.eurostat.jgiscotools.feature.Feature;
import eu.europa.ec.eurostat.jgiscotools.feature.JTSGeomUtil;
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
	private static String namesStruct = basePath + "namesStruct.gpkg";

	// TODO check gazeeter aswell ? check geo coverage.
	// TODO elaborate: different font size and weight depending on population
	// TODO publish as euronyme repository - split by country

	public static void main(String[] args) {
		System.out.println("Start");

		// structure();

		// generate

		// get input labels
		ArrayList<Feature> fs = GeoData.getFeatures(namesStruct);
		System.out.println(fs.size() + " labels loaded");

		//
		fs = generate(fs, 15, 30, 100000, 1.2, 25, 25);
		System.out.println(fs.size());

		// save
		System.out.println("save as GPKG");
		GeoData.save(fs, basePath + "euronymes.gpkg", CRSUtil.getETRS89_LAEA_CRS());
		System.out.println("save as CSV");
		CSVUtil.save(CSVUtil.featuresToCSV(fs), basePath + "v1/EUR.csv");

		System.out.println("End");
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

		// initialise rmax
		for (Feature f : fs)
			f.setAttribute("rmax", resMax);

		for (int res = resMin; res <= resMax; res *= zf) {
			System.out.println("Resolution: " + res);

			// extract only the labels that are visible for this resolution
			final int res_ = res;
			Predicate<Feature> pr = f -> {
				Integer rmax = (Integer) f.getAttribute("rmax");
				return rmax > res_;
			};
			List<Feature> fs_ = fs.stream().filter(pr).collect(Collectors.toList());
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

				Integer rmax = (Integer) f.getAttribute("rmax");
				if (rmax <= res)
					continue;

				// get envelope, enlarged
				Envelope env = (Envelope) f.getAttribute("gl");
				Envelope searchEnv = new Envelope(env);
				searchEnv.expandBy(pixX * res, pixY * res);

				// get other labels overlapping/nearby with index
				List<Feature> neigh = index.query(searchEnv);
				// refine list of neighboors
				Predicate<Feature> pr2 = f2 -> {
					return searchEnv.intersects((Envelope) f2.getAttribute("gl"));
				};
				neigh = neigh.stream().filter(pr2).collect(Collectors.toList());

				// in case no neighboor...
				if (neigh.size() == 1)
					continue;

				// get best label to keep
				Feature toKeep = getBestLabelToKeep(neigh);

				// set rmax of others, and remove them
				neigh.remove(toKeep);
				for (Feature f_ : neigh) {
					f_.setAttribute("rmax", res);
					index.remove((Envelope) f_.getAttribute("gl"), f_);
				}

			}

		}

		// remove attribute
		for (Feature f : fs)
			f.getAttributes().remove("gl");
		for (Feature f : fs)
			f.getAttributes().remove("pop");

		// filter - keep only few
		return (ArrayList<Feature>) fs.stream().filter(f -> (Integer) f.getAttribute("rmax") > resMin)
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
			// NAMN1 NAMN2
			String name = (String) f.getAttribute("NAMN1");
			if (name == null) {
				System.out.println("No NAMN1 for " + f.getID());
				name = (String) f.getAttribute("NAMN2");
				if (name == null) {
					System.out.println("No NAMN1 for " + f.getID());
					continue;
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

			out.add(f_);
		}

		// save output
		System.out.println("Save " + out.size());
		GeoData.save(out, namesStruct, CRSUtil.getETRS89_LAEA_CRS());
	}

	private static ArrayList<Feature> getNameExtend(double pixSize, int fontSize) {
		ArrayList<Feature> fs = GeoData.getFeatures(namesStruct);
		for (Feature f : fs) {
			Envelope env = getLabelEnvelope(f, fontSize, pixSize);
			f.setGeometry(JTSGeomUtil.getGeometry(env));
		}
		return fs;
	}

	/**
	 * @param f        The label object.
	 * @param fontSize The font size to apply.
	 * @param pixSize  The zoom level: size of a pixel in m.
	 * @return
	 */
	private static Envelope getLabelEnvelope(Feature f, int fontSize, double pixSize) {
		Coordinate c = f.getGeometry().getCoordinate();
		double x1 = c.x;
		double y1 = c.y;

		// 12pt = 16px
		double h = pixSize * fontSize * 1.333333;
		double w = h * ((String) f.getAttribute("name")).length();

		return new Envelope(x1, x1 + w, y1, y1 + h);
	}

	/*
	 * /make agent toponymes ArrayList<AgentToponyme> agents = new ArrayList<>();
	 * for(Feature f :fs) agents.add(new AgentToponyme(f)); //make engine
	 * Engine<AgentToponyme> e = new Engine<>(agents); //start e.activateQueue();
	 */

}
