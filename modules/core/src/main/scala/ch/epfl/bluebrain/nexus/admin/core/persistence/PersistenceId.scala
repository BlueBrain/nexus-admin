package ch.epfl.bluebrain.nexus.admin.core.persistence

trait PersistenceId[A] {

  /**
    * Generate persistence id for type [[A]].
    *
    * @param id id to generate the persistence id for
    * @return string persistence id
    */
  def persistenceId(id: A): String
}

object PersistenceId {
  def apply[A](implicit persId: PersistenceId[A]): PersistenceId[A] = persId

  implicit class PersistenceIdOps[A: PersistenceId](id: A) {
    def persistenceId: String = PersistenceId[A].persistenceId(id)
  }
}
