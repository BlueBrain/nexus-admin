# Project operations

**Note:** You might need to add `-H "Authorization: Bearer ***"` if you are attempting to operate on a protected resource using the curl examples.

## Create project

```
PUT /v1/projects/{id}
{...}
```

The project's metadata used and managed by the platform is defined on its [SHACL Schema](https://github.com/BlueBrain/nexus-admin/blob/master/modules/schemas/src/main/resources/schemas/nexus/core/project/v0.1.0.json).
The client can enrich this metadata by adding fields which will be available during search. The fields `@context` or `@type` don't need to be present because they will be injected by the service. 
However, it is possible to add them explicitly or even to define your own `@context` and `type`. 
In this case both the customer's metadata and the injected by the service will be applied (in case of collision, the injected by the platform will override the one provided by the customer).

#### Status Codes

- **201 Created**: the project was created successfully
- **400 Bad Request**: the project's payload is not valid or cannot be created at this time
- **409 Conflict**: the project already exists

#### Example

Request
:   @@snip [project-create.sh](../assets/api-reference/projects/project-create.sh)

Payload
:   @@snip [project-create-payload.json](../assets/api-reference/projects/project-create-payload.json)

Response
:   @@snip [existing-project.json](../assets/api-reference/projects/existing-project.json)



## Update project

```
PUT /v1/projects/{id}?rev={previous_revision}
{...}
```
... where `{previous_rev}` is the last known revision number for the project.


The project's metadata used and managed by the platform is defined on its [SHACL Schema](https://github.com/BlueBrain/nexus-admin/blob/master/modules/schemas/src/main/resources/schemas/nexus/core/project/v0.1.0.json).
The client can enrich this metadata by adding fields which will be available during search. The fields `@context` or `@type` don't need to be present because they will be injected by the service. 
However, it is possible to add them explicitly or even to define your own `@context` and `type`. 
In this case both the customer's metadata and the injected by the service will be applied (in case of collision, the injected by the platform will override the one provided by the customer).

#### Status Codes

- **200 OK**: the project was created successfully
- **400 Bad Request**: the project's payload is not valid or cannot be created at this time
- **409 Conflict**: the provided revision is not the current project's revision number

#### Example

Request
:   @@snip [project-update.sh](../assets/api-reference/projects/project-update.sh)

Payload
:   @@snip [project-create-payload.json](../assets/api-reference/projects/project-create-payload.json)

Response
:   @@snip [existing-project-rev-2.json](../assets/api-reference/projects/existing-project-rev-2.json)


## Deprecate a project

```
DELETE /v1/projects/{id}?rev={previous_revision}
```
... where `{previous_rev}` is the last known revision number for the project.

The projects (as any other resource on the Nexus platform) cannot be deleted but they can be flagged as `deprecated`. The semantics of deprecated is that the project cannot be updated, neither any of its sub-resources.
The deprecation of a resource also increases the `rev`, as any other update.


#### Status Codes

- **200 OK**: the project was deprecated successfully
- **400 Bad Request**: the project is not valid or cannot be created at this time
- **409 Conflict**: the provided revision is not the current project's revision number

#### Example

Request
:   @@snip [project-deprecate.sh](../assets/api-reference/projects/project-deprecate.sh)

Response
:   @@snip [existing-project-rev-2.json](../assets/api-reference/projects/existing-project-rev-2.json)


## Fetch the current revision of the project

```
GET /v1/projects/{id}
```

#### Status Codes

- **200 OK**: the project is found and returned successfully
- **404 Not Found**: the resource was not found

#### Example

Request
:   @@snip [project-get.sh](../assets/api-reference/projects/project-get.sh)

Response
:   @@snip [existing-project.json](../assets/api-reference/projects/existing-project.json)


## Fetch a specific revision of the project

```
GET /v1/projects/{id}?rev={rev}
```
... where `{rev}` is the revision number, starting at `1`.

#### Status Codes

- **200 OK**: the project revision is found and returned successfully
- **404 Not Found**: the resource revision was not found

#### Example

Request
:   @@snip [project-get-rev.sh](../assets/api-reference/projects/project-get-rev.sh)

Response
:   @@snip [existing-project.json](../assets/api-reference/projects/existing-project.json)


## Search projects

```
GET /v1/projects
      ?q={full_text_search_query}
      &context={context}
      &filter={filter}
      &fields={fields}
      &sort={sort}
      &from={from}
      &size={size}
      &deprecated={deprecated}
```
... where all of the query parameters are individually optional.


- `{full_text_search_query}`: String - can be provided to select only the projects that have attribute values matching (containing) the provided token; when this field is provided the results will also include score values for each result
- `{context}`: JsonLd - can be provided to define extra prefix mappings. By default, the search core context will be used. If this field is provided, its content will be merged with the search core context.
- `{filter}`: JsonLd - a filtering expression in JSON-LD format (the structure of the filter is explained below)
- `{fields}`: a comma separated list of fields which are going to be retrieved as a result. The reserved keyword `nxv:all` retrieves all the fields.
- `{sort}`: a comma separated list of fields (absolute qualified URIs) which are going to be used to order the results. Prefixing a field with `-` will result into descending ordering on that field while prefixing it with `+` results in ascending ordering. 
When no prefix is set, the default behaviour is to assume ascending ordering.
- `{from}`: Number - is the parameter that describes the offset for the current query; defaults to `0`
- `{size}`: Number - is the parameter that limits the number of results; defaults to `20`
- `{deprecated}`: Boolean - can be used to filter the resulting projects based on their deprecation status

Filtering example while listing:

List Projects
: @@snip [list-projects.sh](../assets/api-reference/projects/list-projects.sh)

List Projects Response
:   @@snip [projects-list.json](../assets/api-reference/projects/projects-list.json)

Filtering example while searching (notice the additional score related fields):

Search and Filter Projects
: @@snip [search-list-projects.sh](../assets/api-reference/projects/search-list-projects.sh)

Search and Filter Projects Response
:   @@snip [instance-list.json](../assets/api-reference/projects/projects-search-list.json)
