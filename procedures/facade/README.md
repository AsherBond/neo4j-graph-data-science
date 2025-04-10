# Procedure Facade

Here we keep the injectable facade for the GDS procedures.

GDS procedures inject this single dependency, and it forms an umbrella over functional areas like graph store catalog or community algorithms. This keeps each sub interface smaller, more coherent and more manageable.

We also here host the integration services needed to cleanly interface with Neo4j. All Neo4j dependencies should be unstacked and wrapped here, because in the layers below we would want to be Neo4j agnostic. That makes them reusable.

Code in here mainly uses [the applications procedure facade](../algorithms-facade/README.md) and pipelines. 
