# trivial-rest #

### TODO ###

CORE
* Support HTTP PUT by ID
* Don't allow duplicate Resources
* Combine Get and GetAll. There should only be Get.
* Return the IDs of items added, updated and deleted.
* Don't create backups of empty files
* Use aggregate resources to pull back many things at once, e.g. /api/planets { ... all the planets ... }
* Use aggregate resources to form custom queries, e.g. POST /api/query/ PlanetsWithYear(> 12 months) -> { ... all matching planets ... }
* Make the root GET link return a list of resources, e.g. /api/planet -> {link to planet 1, link to planet 2, ... }
* Use special Resources for: Audit, Exceptions, Migration results
* Upgrade to latest version of finatra, for 2.11 support. Ditch 2.10 support.
* Improve error handling as per giant ugly comments in Json4sSerialiser
* Do all the TODOs. Start with Rest.
* Abstract out the controller, so we can use other web frameworks
* Make validation a client-level coding concern (and some sensible defaults)
* Add serialiser for BigDecimal serialisation, so Vector works.
* Write the serialisers so that we can combine them with the converters used for shapeless support
* Add serialiser for Joda time classes, with joda as a "provided" dependency

PERSISTER
* Make update an atomic operation
* Rename methods, so we just have CRUD

VALIDATOR
* Seriously needs some love. Start with the concept of a Validation

DISPLAY
* A REST-supporting GUI, that acts like a DB admin tool, and allows for easy CRUD operations.
