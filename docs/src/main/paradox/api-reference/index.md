@@@ index

* [projects](projects.md)

@@@

# API Reference

As with all Nexus services the Nexus Administration exposes a RESTful interface over HTTP(S) for synchronous communication. 
The generally adopted transport format is JSON based, specifically [JSON-LD](https://json-ld.org/).

The service operates on `projects` and `users` (TBD) resources. 

- A project is the top level grouping for any sub-resource in Nexus. 
The metadata (configuration, policies) defined on a project can be used by the sub-resources. The ACLs defined on a project will apply on their sub-resources.


## Resource Lifecycle

All resources in the system generally follow the very same lifecycle, as depicted in the diagram below.  Changes to the
data (creation, updates, state changes) are recorded into the system as revisions.  This functionality is made possible
by the [event sourced](https://martinfowler.com/eaaDev/EventSourcing.html) persistence strategy that is used in the
underlying primary store of the system.

![Resource Lifecycle](../assets/api-reference/resource-lifecycle.png)

Data is never removed from the system, but rather is marked as deprecated. 
A deprecated resource is a resource that cannot be updated and that cannot update any of its sub-resources.


## Error Signaling

The services makes use of the HTTP Status Codes to report the outcome of each API call.  The status codes are
complemented by a consistent response data model for reporting client and system level failures.

Format
:   @@snip [error.json](../assets/api-reference/error.json)

Example
:   @@snip [error-example.json](../assets/api-reference/error-example.json)

While the format only specifies `code` and `message` fields, additional fields may be presented for additional
information in certain scenarios.


## Filter expressions

Filters follow the general form:

```
comparisonOp    = 'eq' | 'ne' | 'lt' | 'lte' | 'gt' | 'gte' | 'in'
logicalOp       = 'and' | 'or' | 'not' | 'xor'
op              = comparisonOp | logicalOp

path            = uri | property path
comparisonValue = literal | uri | {comparisonValue}

comparisonExpr  = json {
                      "op": comparisonOp,
                      "path": path,
                      "value": comparisonValue
                    }

logicalExpr     = json {
                      "op": logicalOp,
                      "value": {filterExpr}
                    }

filterExpr      = logicalExpr | comparisonExpr

json {
  "@context": {...},
  "filter": filterExpr
}
```
... which roughly means:

- a filter is a json-ld document
- that describes a filter value as a filter expression
- a filter expression is either a comparison expression or a logical expression
- a comparison expression contains a path property (a uri or a [property path](https://www.w3.org/TR/sparql11-query/#propertypaths)), the value to compare and an
  operator which describes how to compare that value
- a logical expression contains a collection of filter expressions joined together through a logical operator


Example filters:

Comparison
:   @@snip [simple-filter.json](../assets/api-reference/filter/simple-filter.json)

With context
:   @@snip [simple-filter-with-context.json](../assets/api-reference/filter/simple-filter-with-context.json)

Nested filter
:   @@snip [nested-filter.json](../assets/api-reference/filter/nested-filter.json)

Property path
:   @@snip [property-path-filter.json](../assets/api-reference/filter/property-path-filter.json)

Property path with context
:   @@snip [property-path-context-filter.json](../assets/api-reference/filter/property-path-context-filter.json)

## Search response format

The response to any search requests follows the described format:

```
{
  "total": {hits},
  "maxScore": {max_score},
  "results": [
    {
      "score": {score_id},
      "source": {
        "@id": {id},
      }
    },
    {
      ...
    }
  ],
  "links": 
    {
      "@context": "https://nexus.example.com/v1/contexts/links",
      "next": "https://nexus.example.com/v1/{address}?from=40&size=20",
      "previous": "https://nexus.example.com/v1/{address}?from=0&size=20",
      "self": "https://nexus.example.com/v1/{address}?from=20&size=20"
    }
  ]
}
```

...where

* `{hits}` is the total number of results found for the requested search.
* `{maxScore}` is the maximum score found across all hits.
* `{score_id}` is the score for this particular resource

The relationships `next` and `previous` at the top level offer discovery of more resources, in terms of navigation/pagination. 

The fields `{maxScore}` and `{score_id}` are optional fields and will only be present whenever a `q` query parameter is provided on the request.