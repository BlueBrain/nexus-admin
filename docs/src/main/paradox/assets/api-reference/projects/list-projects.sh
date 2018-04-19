curl -XGET 'https://nexus.example.com/v1/projects
      ?from=20&size=20&deprecated=true
      &filter=%7B%22op%22%3A%22lt%22%2C%22path%22%3A%22nxv%3Arev%22%2C%22value%22%3A3%7D'