# trivial-rest #

### TODO ###

* Upgrade to latest version of finatra, for 2.11 support. Ditch 2.10 support.
* Add serialiser for BigDecimal serialisation, so Vector works.
* Write the serialisers so that we can combine them with the converters used for shapeless support
* Add serialiser for Joda time classes, with joda as a "provided" dependency
* Abstract out the controller, so we can use other web frameworks
* Make validation a client-level coding concern (and some sensible defaults)