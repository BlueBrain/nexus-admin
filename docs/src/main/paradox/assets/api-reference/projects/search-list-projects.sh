curl -XGET 'https://nexus.example.com/v1/projects
     ?q=test
     &filter={"op":"gte","path":"nxv:rev","value":2}'