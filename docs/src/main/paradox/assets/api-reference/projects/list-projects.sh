curl -XGET 'https://nexus.example.com/v1/projects
      ?from=20&size=20&deprecated=true
      &filter={"op":"lt","path":"nxv:rev","value":3}'