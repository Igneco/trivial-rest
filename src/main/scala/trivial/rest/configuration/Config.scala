package trivial.rest.configuration

case class Config(
  // Serialise nested resources as their ID, instead of nesting another JSON tree
  flattenNestedResources: Boolean = true
)