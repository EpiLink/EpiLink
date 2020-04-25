# Changelog

[README](/README.md) | [Documentation](/docs/README.md) | CHANGELOG

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased (0.1.1)

### Added

* Added the ability to set a logo on the back-end (#111)
* Fixed the 'remember me' checkbox on the profile page sometimes not being selected when it should be (#124)

### Fixed

* Fixed CORS support on HTTPS (#115)
* Various building fixes (#118, #110)

### Removed

* Removed HTTPS redirection options (#120)

## 0.1

Initial release. Introduces so many things it will make your eyes hurt, probably.

### Added

* Added license checking for the project (#103)
* Added profile page (#107)
* Added reverse proxy and HTTPS redirection support (#93, #98)
* Added rate-limiting (#93)
* Added relinking endpoints and front-end support (#85, #107)
* Added i18n support to the front-end (#84, #107)
* Added e-mail validation to rulebooks (#80)
* Added logging all over the place (#74)
* Added `/user` endpoints (#60, #76, #102)
* Added `/meta` endpoints (#59)
* Added testing (#49, #79)
* Added Redis server support (#48)
* Added embeds for ID access notifications and greetings message (#44)
* Added proper error-handling (#36, #42)
* Added automated ID access notifications (#31 but this predates that PR)
* Use dependency injection for everything via Koin (#24)
* Added the front-end (#23, #89, #90, #91, #92, )
* Added rulebooks (#19)
* Added Discord bot (#18)
* Added the maintainer guide (#17)
* Added registration on the back-end (#16)
* Added the ability for the front-end to be bundled to the back-end (#14)
* Added some documentation (all PRs, notably #47, #77, #87)
* Added the back-end API (#13) 
* Added CLI arguments support (#12)
* Added database support (#8)
* Added Ktor server and back-end server (#5, #13)
* Added basic GitHub project management via CI and code owners (#4, #49)
* Added basic Gradle project (#2)