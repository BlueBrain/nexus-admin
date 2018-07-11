curl -XPUT -H "Content-Type: application/json" "https://nexus.example.com/v1/projects/myproject?rev=1"
    -d '{"name": "some name", "description": "The project description", "prefixMappings": [{"prefix": "nxv", "namespace": "https://bluebrain.github.io/nexus/vocabulary/"}, {"prefix": "persons", "namespace": "https://shapes-registry.org/commons/person"} ], "config": {"maxAttachmentSize": 10 } }'
