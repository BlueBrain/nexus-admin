curl -XGET 'https://nexus.example.com/v1/projects
     ?q=test
     &filter=%7B%22op%22%3A%22gte%22%2C%22path%22%3A%22nxv%3Arev%22%2C%22value%22%3A2%7D'