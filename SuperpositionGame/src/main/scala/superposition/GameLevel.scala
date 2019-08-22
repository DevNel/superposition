package superposition

import engine.core.Behavior.Entity
import engine.core.Game.{declareSystem, dt}
import engine.core.Input
import engine.graphics.Camera
import engine.graphics.Camera.Camera2d
import engine.graphics.opengl.{Framebuffer, Shader, Texture}
import engine.graphics.sprites.Sprite
import engine.util.Color._
import engine.util.math.{Transformation, Vec2d}
import org.lwjgl.glfw.GLFW._

import scala.math.{Pi, pow, sqrt}

private object GameLevel {
  private object Gate extends Enumeration {
    val X, Z, T, H = Value
  }

  private val NumObjects: Int = 2
  private val UniverseShader: Shader = Shader.load("universe")

  def init(): Unit =
    declareSystem(classOf[GameLevel], (level: GameLevel) => level.step())
}

private class GameLevel extends Entity {
  import GameLevel._

  private var universes: List[Universe] = List(new Universe(NumObjects))
  private var frameBuffer: Framebuffer = _
  private var colorBuffer: Texture = _
  private var time: Double = 0.0

  override protected def onCreate(): Unit = {
    frameBuffer = new Framebuffer()
    colorBuffer = frameBuffer.attachColorBuffer()
  }

  private def applyGate(gate: Gate.Value, target: Int, controls: Int*): Unit = {
    for (u <- universes.filter(u => controls.forall(u.particles(_).on))) {
      gate match {
        case Gate.X => u.particles(target).on = !u.particles(target).on
        case Gate.Z =>
          if (u.particles(target).on) {
            u.amplitude *= Complex(-1.0)
          }
        case Gate.T =>
          if (u.particles(target).on) {
            u.amplitude *= Complex.polar(1.0, Pi / 4.0)
          }
        case Gate.H =>
          u.amplitude /= Complex(sqrt(2.0))
          val copy = u.copy()
          if (u.particles(target).on) {
            u.amplitude *= Complex(-1.0)
          }
          copy.particles(target).on = !copy.particles(target).on
          universes = copy :: universes
      }
    }
  }

  private def combine(): Unit =
    universes = universes
      .groupMapReduce(_.state)(identity)((u1, u2) => {
        u2.amplitude += u1.amplitude
        u2
      })
      .values
      .filter(_.amplitude.magnitudeSquared > 1e-6)
      .toList

  private def normalize(): Unit = {
    val sum = universes.map(_.amplitude.magnitudeSquared).sum
    for (u <- universes) {
      u.amplitude /= Complex(sqrt(sum))
    }
  }

  private def step(): Unit = {
    val selected = universes
      .flatMap(_.particles.zipWithIndex)
      .filter(_._1.position.sub(Input.mouse()).length() < 0.5)
      .map(_._2)
      .toSet
    for (i <- selected) {
      if (Input.keyJustPressed(GLFW_KEY_X)) {
        applyGate(Gate.X, i)
      }
      if (Input.keyJustPressed(GLFW_KEY_Z)) {
        applyGate(Gate.Z, i)
      }
      if (Input.keyJustPressed(GLFW_KEY_T)) {
        applyGate(Gate.T, i)
      }
      if (Input.keyJustPressed(GLFW_KEY_H)) {
        applyGate(Gate.H, i)
      }
    }

    for (u <- universes) {
      u.step()
    }
    combine()
    normalize()
    draw()
  }

  private def draw(): Unit = {
    time += dt()
    UniverseShader.setUniform("time", time.asInstanceOf[Float])

    var minVal = 0.0
    for (u <- universes) {
      val maxVal = minVal + u.amplitude.magnitudeSquared

      frameBuffer.clear(CLEAR)
      for (p <- u.particles) {
        val color = if (p.on) WHITE else BLACK
        Sprite.load("cat.png").draw(Transformation.create(p.position, 0, 1), color)
      }

      val camera = new Camera2d()
      camera.lowerLeft = new Vec2d(-1, -1)
      Camera.current = camera
      UniverseShader.setMVP(Transformation.IDENTITY)
      UniverseShader.setUniform("minVal", minVal.asInstanceOf[Float])
      UniverseShader.setUniform("maxVal", maxVal.asInstanceOf[Float])
      UniverseShader.setUniform("hue", (u.amplitude.phase / (2.0 * Pi)).asInstanceOf[Float])
      Framebuffer.drawToWindow(colorBuffer, UniverseShader)
      Camera.current = Camera.camera2d

      minVal = maxVal
    }
  }
}
