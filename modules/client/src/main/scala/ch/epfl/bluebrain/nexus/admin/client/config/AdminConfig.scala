package ch.epfl.bluebrain.nexus.admin.client.config

import akka.http.scaladsl.model.Uri

/**
  * @param baseUri the Admin service projects management endpoint, preferably including a trailing slash
  *                e.g. ''https://nexus.example.com/projects/''
  */
final case class AdminConfig(baseUri: Uri)
