# Euronym

Euronym provides ready-to-use toponym files for multi-scale web mapping applications, with a focus on Europe. Euronym files are produced using a [label placement algorithm](https://en.wikipedia.org/wiki/Automatic_label_placement) to adapt the density, selection and size of labels depending on the zoom level.

[![](/docs/overview.gif)](https://eurostat.github.io/gridviz/examples/labels_.html)

## Example

Euronym is used by [Gridviz](https://github.com/eurostat/gridviz/blob/master/docs/reference.md#showing-labels) library. See [this example](https://eurostat.github.io/gridviz/examples/labels_.html).

## API

Base URL: `https://raw.githubusercontent.com/euronym/Nuts2json/master/pub/v2`
(See [here](#own-deployment) how to define your own base URL)

URL patterns:  `/<ENCODING>/<RESOLUTION>/<GEO>.csv`

For example, [`https://raw.githubusercontent.com/eurostat/euronym/main/pub/v2/UTF/100/LU.csv`](https://raw.githubusercontent.com/eurostat/euronym/main/pub/v2/UTF/100/LU.csv) returns UTF data for resolution 100m/pixel over Luxembourg.

The parameters are:

| Parameter | Supported values | Description |
| ------------- | ------------- |-------------|
| `ENCODING` | `UTF` `ASCII` | The encoding. By default, use `UTF`. |
| `RESOLUTION` | `20` `50` `100` `200` | The most detailled resolution. The unit is the zoom level of your visualisation expressed in m/pixel. TODO: give correspondence with usual zoom level values. |
| `GEO` | See for example [here](https://github.com/eurostat/euronym/tree/main/pub/v2/UTF/20). `EUR` is for the entire dataset. | The code of the geographic entity to cover. |

For additional parameters, feel free to [ask](https://github.com/eurostat/euronym/issues/new) !

## Format

A CSV file with the following columns:

| Column | Description |
| ------------- | ------------- |
| `name` | The toponym text. |
| `lon` | The longitude.  |
| `lat` | The latitude. |
| `r1` | TODO |
| `rs` | TODO |

Longitude and latitude are expressed in ETRS89 ([EPSG 4258](https://spatialreference.org/ref/epsg/etrs89/)).

## Input data

Euronym relies on the following datasets:

- [EuroRegionalMap](https://eurogeographics.org/maps-for-europe/euroregionalmap/) by [eurogeographics](https://eurogeographics.org/)
- [European commission town names table](https://ec.europa.eu/regional_policy/en/information/maps/urban-centres-towns)

TODO document process

## Own deployment

To deploy your own stable version of Euronym, `git clone` the repository and simply publish the `pub` folder on your own web server folder accessible under the URL of your choice. Use the new base URL, for example `https://www.mydomain.eu/path/to/pub/v2`, to access the service. This offers the possibility to select only the necessary API elements of interest.


## About

| | |
|-|-|
| *contributors* | [<img src="https://github.com/jgaffuri.png" height="40" />](https://github.com/jgaffuri)  |
| *version* | 2 |
| *status* | Since 2022 |
| *license* | [EUPL 1.2](https://github.com/eurostat/Nuts2json/blob/master/LICENSE) |


## Support and contribution

Feel free to [ask support](https://github.com/eurostat/euronym/issues/new), fork the project or simply star it (it's always a pleasure).


## Copyright

The copyright of the input datasets apply:

- [EuroGeographics Open Data Licence](https://www.mapsforeurope.org/licence). [EuroRegionalMap](https://eurogeographics.org/maps-for-europe/euroregionalmap/) dataset is protected by copyright and other applicable laws. It is provided under the terms of this licence, which you accept by using it and accepting the terms of the licence. Note that **© EuroGeographics 2022** attribution must be added.


## Disclaimer

The designations employed and the presentation of material on these maps do not imply the expression of any opinion whatsoever on the part of the European Union concerning the legal status of any country, territory, city or area or of its authorities, or concerning the delimitation of its frontiers or boundaries. Kosovo*: This designation is without prejudice to positions on status, and is in line with UNSCR 1244/1999 and the ICJ Opinion on the Kosovo declaration of independence. Palestine*: This designation shall not be construed as recognition of a State of Palestine and is without prejudice to the individual positions of the Member States on this issue.
