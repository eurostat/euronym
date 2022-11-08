# Euronym

Multi-scale toponyms for web mapping applications. For Europe.



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

