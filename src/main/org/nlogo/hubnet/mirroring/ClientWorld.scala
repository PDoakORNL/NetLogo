package org.nlogo.hubnet.mirroring

import java.util.Comparator

private object ClientWorldS {

  case class TurtleKey(who: Long, breedIndex: Int)
  private val turtleOrdering = implicitly[Ordering[(Int, Long)]]
  class TurtleKeyComparator extends Comparator[TurtleKey] {
    override def compare(key1: TurtleKey, key2: TurtleKey) =
      turtleOrdering.compare((key1.breedIndex, key1.who),
                             (key2.breedIndex, key2.who))
  }

  case class LinkKey(id: Long, end1: Long, end2: Long, breedIndex: Int)
  private val linkOrdering = implicitly[Ordering[(Long, Long, Int, Long)]]
  class LinkKeyComparator extends Comparator[LinkKey] {
    override def compare(key1: LinkKey, key2: LinkKey): Int =
      linkOrdering.compare((key1.end1, key1.end2, key1.breedIndex, key1.id),
                           (key2.end1, key2.end2, key2.breedIndex, key2.id))
  }

}

import org.nlogo.api

class ClientWorld(printErrors: Boolean = true, numPatches: Option[java.lang.Integer] = None)
extends ClientWorldJ(printErrors) with Overrides with Updating with Unsupported {

  var patchData: Array[PatchData] = null
  var patchColors: Array[Int] = null

  for(num <- numPatches)
    createPatches(num.intValue)

  // temporary hack for the review tab experiments
  def reset() {
    import org.nlogo.hubnet.mirroring.ClientWorldS.{ TurtleKeyComparator, LinkKeyComparator }
    sortedTurtles = new java.util.TreeMap(new TurtleKeyComparator)
    turtleKeys = new java.util.HashMap
    sortedLinks = new java.util.TreeMap(new LinkKeyComparator)
    linkKeys = new java.util.HashMap
  }

  override def createPatches(numPatches: Int) {
    patchData = new Array(numPatches)
    patchColors = new Array(numPatches)
    for(i <- 0 until numPatches) {
      patchData(i) = new PatchData(i, PatchData.COMPLETE.toShort, 0, 0, Double.box(0), "", Double.box(0))
      patchData(i).patchColors = patchColors
    }
  }

}

trait Unsupported extends ClientWorldJ {
  override def links = unsupported
  override def turtles = unsupported
  override def patches = unsupported
  override def program = unsupported
  override def turtleShapeList = unsupported
  override def linkShapeList = unsupported
  override def patchesWithLabels = unsupported
  override def getPatch(i: Int) = unsupported
  override def getPatchAt(x: Double, y: Double) = unsupported
  override def observer = unsupported
  override def getDrawing = unsupported
  override def sendPixels = unsupported
  override def markDrawingClean = unsupported
  override def protractor = unsupported
  override def wrappedObserverX(x: Double) = unsupported
  override def wrappedObserverY(y: Double) = unsupported
  override def markPatchColorsClean = unsupported
  override def markPatchColorsDirty = unsupported
  override def patchColorsDirty = unsupported
  override def fastGetPatchAt(x: Int, y: Int) = unsupported
  override def getVariablesArraySize(link: api.Link, breed: api.AgentSet) = unsupported
  override def linksOwnNameAt(i: Int) = unsupported
  override def getVariablesArraySize(turtle: api.Turtle, breed: api.AgentSet) = unsupported
  override def turtlesOwnNameAt(i: Int) = unsupported
  override def breedsOwnNameAt(breed: api.AgentSet, i: Int) = unsupported
  override def allStoredValues = unsupported
  override def mayHavePartiallyTransparentObjects = false
  override def ticks = unsupported
  private def unsupported = throw new UnsupportedOperationException
}

trait Updating extends ClientWorldJ with AgentUpdaters {

  import api.AgentException

  var minPxcor = -1
  var maxPxcor = -1
  var minPycor = -1
  var maxPycor = -1

  def setWorldSize(minx: Int, maxx: Int, miny: Int, maxy: Int) {
    minPxcor = minx
    maxPxcor = maxx
    minPycor = miny
    maxPycor = maxy
    createPatches(worldWidth * worldHeight)
  }

  @throws(classOf[AgentException])
  def wrapX(x: Double) = {
    val max = maxPxcor + 0.5
    val min = minPxcor - 0.5
    if (!xWrap) {
      if (x >= max || x < min)
        throw new AgentException("Cannot move turtle beyond the world's edge.")
      x
    }
    else wrap(x, min, max)
  }

  @throws(classOf[AgentException])
  def wrapY(y: Double) = {
    val max = maxPycor + 0.5
    val min = minPycor - 0.5
    if (!yWrap) {
      if (y >= max || y < min)
        throw new AgentException("Cannot move turtle beyond the world's edge.")
      y
    }
    else wrap(y, min, max)
  }

  def wrap(pos: Double, min: Double, max: Double) =
    if (pos >= max)
      (min + ((pos - max) % (max - min)))
    else if (pos < min) {
      val result = ((min - pos) % (max - min))
      if (result == 0) min else (max - result)
    }
    else pos

  var shapes = false
  def shapesOn = shapes

  private var _fontSize = 0
  def fontSize = (_fontSize * zoom).toInt

  private var xWrap = false
  private var yWrap = false
  def wrappingAllowedInX = xWrap
  def wrappingAllowedInY = yWrap

  @throws(classOf[java.io.IOException])
  def updateFrom(is: java.io.DataInputStream) {
    val mask = is.readShort()
    var reallocatePatches = false
    if ((mask & DiffBuffer.MINX) == DiffBuffer.MINX) {
      val minx = is.readInt()
      reallocatePatches = reallocatePatches || minx != minPxcor
      minPxcor = minx
    }
    if ((mask & DiffBuffer.MINY) == DiffBuffer.MINY) {
      val miny = is.readInt()
      reallocatePatches = reallocatePatches || miny != minPycor
      minPycor = miny
    }
    if ((mask & DiffBuffer.MAXX) == DiffBuffer.MAXX) {
      val maxx = is.readInt()
      reallocatePatches = reallocatePatches || maxx != maxPxcor
      maxPxcor = maxx
    }
    if ((mask & DiffBuffer.MAXY) == DiffBuffer.MAXY) {
      val maxy = is.readInt()
      reallocatePatches = reallocatePatches || maxy != maxPycor
      maxPycor = maxy
    }
    if (reallocatePatches)
      createPatches(worldWidth * worldHeight)
    if ((mask & DiffBuffer.SHAPES) == DiffBuffer.SHAPES)
      shapes = is.readBoolean()
    if ((mask & DiffBuffer.FONT_SIZE) == DiffBuffer.FONT_SIZE)
      _fontSize = is.readInt()
    if ((mask & DiffBuffer.WRAPX) == DiffBuffer.WRAPX)
      xWrap = is.readBoolean()
    if ((mask & DiffBuffer.WRAPY) == DiffBuffer.WRAPY)
      yWrap = is.readBoolean()
    if ((mask & DiffBuffer.PERSPECTIVE) == DiffBuffer.PERSPECTIVE)
      updateServerPerspective(new AgentPerspective(is))
    if ((mask & DiffBuffer.PATCHES) == DiffBuffer.PATCHES)
      for(_ <- 0 until is.readInt())
        updatePatch(PatchData.fromStream(is))
    if ((mask & DiffBuffer.TURTLES) == DiffBuffer.TURTLES)
      for(_ <- 0 until is.readInt())
        updateTurtle(TurtleData.fromStream(is))
    if ((mask & DiffBuffer.LINKS) == DiffBuffer.LINKS)
      for(_ <- 0 until is.readInt())
        updateLink(LinkData.fromStream(is))
    if ((mask & DiffBuffer.DRAWING) == DiffBuffer.DRAWING)
      trailDrawer.readImage(is)
  }
}

trait AgentUpdaters extends ClientWorldJ {

  import ClientWorldS.TurtleKey
  import ClientWorldS.LinkKey

  def updatePatch(patch: PatchData) {
    if (patch.id >= getPatches().length) {
      handleError("ERROR: received update for " + "non-existent patch (" + patch.stringRep + ").")
      return
    }
    // otherwise, we'll need our version, if we've got one.
    val bufPatch = getPatches.apply(patch.id.toInt)
    // if we haven't got one, this patch better have all its info...
    if (bufPatch == null && !patch.isComplete)
      handleError(
        "ERROR: received incremental update for non-existent patch (" + patch.stringRep + ").")
    // otherwise, perform the update...
    bufPatch.updateFrom(patch)
    patchColors()(patch.id.toInt) = api.Color.getARGBIntByRGBAList(bufPatch.pcolor)
  }

  def updateTurtle(turtle: TurtleData) {
    val simpleKey = Long.box(turtle.id)
    var sortedKey = turtleKeys.get(simpleKey)
    // if this turtle has died, just remove it...
    if (turtle.isDead) {
      if (sortedKey == null) {
        handleError("ERROR: received death message for " + "non-existent turtle (" + turtle.stringRep + ").")
        return
      }
      sortedTurtles.remove(sortedKey)
      turtleKeys.remove(simpleKey)
      return
    }
    // otherwise, we'll need our version, if we've got one.
    var bufTurtle: TurtleData = null
    if (sortedKey == null) {
      bufTurtle = uninitializedTurtles.get(simpleKey)
      if (bufTurtle != null) {
        sortedKey = new TurtleKey(turtle.id, turtle.getBreedIndex)
        sortedTurtles.put(sortedKey, bufTurtle)
        turtleKeys.put(simpleKey, sortedKey)
        uninitializedTurtles.remove(simpleKey)
      }
    }
    if (sortedKey != null)
      bufTurtle = sortedTurtles.get(sortedKey)
    // if we haven't got one, this turtle better have all its info...
    if (bufTurtle == null) {
      if (turtle.isComplete) {
        sortedKey = new TurtleKey(turtle.id, turtle.getBreedIndex)
        sortedTurtles.put(sortedKey, turtle)
        turtleKeys.put(simpleKey, sortedKey)
      }
      else handleError(
        "ERROR: received incremental update for non-existent turtle (" + turtle.stringRep + ").")
      return
    }
    // otherwise, perform the update...
    bufTurtle.updateFrom(turtle)
    if (bufTurtle.getBreedIndex != sortedKey.breedIndex) {
      // the breed of this turtle changed so we need to make
      // a new key in the sortedTurtles map ev 5/19/08
      sortedTurtles.remove(sortedKey)
      sortedKey = new TurtleKey(bufTurtle.id, turtle.getBreedIndex)
      sortedTurtles.put(sortedKey, bufTurtle)
      turtleKeys.put(simpleKey, sortedKey)
    }
  }

  def updateLink(link: LinkData) {
    val simpleKey = Long.box(link.id)
    var sortedKey = linkKeys.get(simpleKey)
    if (link.isDead) {
      if (sortedKey == null) {
        handleError("ERROR: received death message for non-existent link ( " + link.stringRep + " ).")
        return
      }
      sortedLinks.remove(sortedKey)
      linkKeys.remove(simpleKey)
      return
    }
    var bufLink: LinkData = null
    if (sortedKey == null) {
      bufLink = uninitializedLinks.get(simpleKey)
      if (bufLink != null) {
        sortedKey = new LinkKey(link.id, link.end1Id, link.end2Id, link.getBreedIndex)
        linkKeys.put(simpleKey, sortedKey)
        sortedLinks.put(sortedKey, link)
        uninitializedLinks.remove(simpleKey)
      }
    }
    if (sortedKey != null)
      bufLink = sortedLinks.get(sortedKey)
    // if we haven't got one, this link better have all its info...
    if (bufLink == null) {
      if (link.isComplete) {
        sortedKey = new LinkKey(link.id, link.end1Id, link.end2Id, link.getBreedIndex)
        linkKeys.put(simpleKey, sortedKey)
        sortedLinks.put(sortedKey, link)
      }
      else handleError(
        "ERROR: received incremental update for " + "non-existent turtle (" + link.stringRep + ").")
    }
    else {
      bufLink.updateFrom(link)
      if (link.isComplete || bufLink.getBreedIndex != sortedKey.breedIndex) {
        sortedLinks.remove(sortedKey)
        sortedKey = new LinkKey(link.id, link.end1Id, link.end2Id, link.getBreedIndex)
        sortedLinks.put(sortedKey, bufLink)
        linkKeys.put(simpleKey, sortedKey)
      }
    }
  }

  private def handleError(x: AnyRef) {
    if (printErrors)
      Console.err.println("@ " + new java.util.Date + " : " + x.toString)
  }

}

trait Overrides extends ClientWorldJ {

  private val overrideMap =
    collection.mutable.Map[Overridable, collection.mutable.Map[Int, AnyRef]]()

  def clearOverrides() {
    overrideMap.clear()
  }

  def updateOverrides(list: SendOverride) {
    list.`type` match {
      case AgentType.Turtle =>
        for(id <- list.overrides.keySet)
          addOverride(getTurtle(id), list.variable, list.overrides(id))
      case AgentType.Patch =>
        for (id <- list.overrides.keySet)
          addOverride(patchData.apply(id.intValue), list.variable, list.overrides(id))
      case AgentType.Link =>
        for (id <- list.overrides.keySet)
          addOverride(getLink(id), list.variable, list.overrides(id))
      case _ =>
    }
  }

  private def addOverride(rider: Overridable, variable: Int, value: AnyRef) {
    val innerMap = overrideMap.getOrElseUpdate(rider, collection.mutable.HashMap())
    innerMap(variable) = value
  }

  def updateOverrides(list: ClearOverride) {
    list.`type` match {
      case AgentType.Turtle =>
        for (id <- list.agents)
          removeOverride(getTurtle(id), list.variable)
      case AgentType.Patch =>
        for (id <- list.agents)
          removeOverride(patchData.apply(id.intValue), list.variable)
      case AgentType.Link =>
        for (id <- list.agents)
          removeOverride(getLink(id), list.variable)
      case _ =>
    }
  }

  private def removeOverride(rider: Overridable, variable: Int) {
    for(map <- overrideMap.get(rider))
      map -= variable
  }

  def applyOverrides() {
    for {
      (rider, overrides) <- overrideMap
      (variable, value) <- overrides
    } rider.set(variable, value)
  }

  def rollbackOverrides() {
    for (rider <- overrideMap.keySet)
      rider.rollback()
  }

}
