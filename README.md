# Euronym

Place name labels for multi-scale web mapping applications, with a focus on Europe.

Euronym labels are produced using a [label placement algorithm](https://en.wikipedia.org/wiki/Automatic_label_placement) to adapt the density, selection and size of labels depending on the zoom level and label importance.

[![](/docs/overview.gif)](https://eurostat.github.io/gridviz/examples/basics/labels_.html)

## Examples

Euronym is used with [Leaflet](https://leafletjs.com/) library. See [this example](https://observablehq.com/d/b1fbb3b3e3255645).

Euronym is used with [Gridviz](https://github.com/eurostat/gridviz/blob/master/docs/reference.md#labellayer) library. See [this example](https://eurostat.github.io/gridviz/examples/basics/labels_.html).

## API

Base URL: `https://raw.githubusercontent.com/euronym/Nuts2json/master/pub/v2`
(See [here](#own-deployment) how to define your own base URL)

URL pattern:  `/<ENCODING>/<RESOLUTION>/<GEO>.csv`

For example, [`https://raw.githubusercontent.com/eurostat/euronym/main/pub/v2/UTF/100/LU.csv`](https://raw.githubusercontent.com/eurostat/euronym/main/pub/v2/UTF/100/LU.csv) returns UTF data for resolution 100m/pixel over Luxembourg.

The parameters are:

| Parameter | Supported values | Description |
| ------------- | ------------- |-------------|
| `ENCODING` | `UTF_LATIN` `UTF` `ASCII` | The encoding. By default, you should use `UTF_LATIN`. |
| `RESOLUTION` | `20` `50` `100` `200` | The most detailled resolution. The unit is the zoom level of your visualisation expressed in *m/pixel*. If small, more labels are necessary and the file gets larger. |
| `GEO` | See for example [here](https://github.com/eurostat/euronym/tree/main/pub/v2/UTF/20). `EUR` is for the entire dataset. | The code of the geographic entity to cover. |

For additional parameters, feel free to [ask](https://github.com/eurostat/euronym/issues/new) !

## Format

A CSV file with the following columns:

| Column | Description |
| ------------- | ------------- |
| `name` | The place name text, to be written on the map. |
| `lon` | The longitude.  |
| `lat` | The latitude. |
| `rs` | Above this resolution, the label should not be shown. |
| `r1` | Above this resolution, the label may be exagerated. |
| `cc` | The country code. This is provided when `GEO` is set to `EUR`. This allows doing some filtering based on country. |

The resolutions are expressed in *m/pixel*: This is the size of a screen pixel in ground meter. The smaller, the more detail.

*rs* and *r1* values are computed so that:
- For resolutions above *rs*, the label should not be shown,
- For resolutions within [*rs*, *r1*], the label should be shown with a *1em* size,
- For resolutions above *r1*, the label should be shown with a *1.5em* size (that is exagerated).

Longitude and latitude are expressed in ETRS89 ([EPSG 4258](https://spatialreference.org/ref/epsg/etrs89/)), which can be considered as identical to WGS84.

## Input data

Euronym relies on the following datasets:

- [EuroRegionalMap](https://eurogeographics.org/maps-for-europe/euroregionalmap/), by [EuroGeographics](https://eurogeographics.org/) (class *BuiltupP*).
- [European commission town names table](https://ec.europa.eu/regional_policy/information-sources/maps/urban-centres-towns_en)

The transformation process is available [here](https://github.com/eurostat/euronym/tree/main/src/), based on [GeoTools library](https://www.geotools.org).

## Own deployment

To deploy your own stable version of Euronym, `git clone` the repository and simply publish the `pub` folder on your own web server folder accessible under the URL of your choice. Use the new base URL, for example `https://www.mydomain.eu/path/to/pub/v2`, to access the service. This offers the possibility to select only the necessary API elements of interest.


## About

| | |
|-|-|
| *contributors* | [<img src="https://github.com/jgaffuri.png" height="40" />](https://github.com/jgaffuri)  |
| *version* | 3 |
| *status* | Since 2022 |
| *license* | [EuroGeographics Open Data Licence](https://www.mapsforeurope.org/licence), [EUPL 1.2](https://github.com/eurostat/Nuts2json/blob/master/LICENSE) for the code. |


## Support and contribution

Feel free to [ask support](https://github.com/eurostat/euronym/issues/new), fork the project or simply star it (it's always a pleasure).


## Copyright

The copyright of the input datasets apply:

- [EuroGeographics Open Data Licence](https://www.mapsforeurope.org/licence). [EuroRegionalMap](https://eurogeographics.org/maps-for-europe/euroregionalmap/) dataset is protected by copyright and other applicable laws. It is provided under the terms of this licence, which you accept by using it and accepting the terms of the licence. Note that **© EuroGeographics 2024** attribution must be added.


## Disclaimer

The designations employed and the presentation of material on these maps do not imply the expression of any opinion whatsoever on the part of the European Union concerning the legal status of any country, territory, city or area or of its authorities, or concerning the delimitation of its frontiers or boundaries. Kosovo*: This designation is without prejudice to positions on status, and is in line with UNSCR 1244/1999 and the ICJ Opinion on the Kosovo declaration of independence. Palestine*: This designation shall not be construed as recognition of a State of Palestine and is without prejudice to the individual positions of the Member States on this issue.
