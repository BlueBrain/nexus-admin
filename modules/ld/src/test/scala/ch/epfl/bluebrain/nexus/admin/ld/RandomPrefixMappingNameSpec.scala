package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}
class RandomPrefixMappingNameSpec extends WordSpecLike with Matchers with Inspectors with Randomness with EitherValues {

  "A random PrefixName" should {
    "be constructed correctly" in {
      List.fill(100)(randomPrefix())
    }
  }
}
