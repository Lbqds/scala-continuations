import com.typesafe.tools.mima.plugin.{MimaPlugin, MimaKeys}
import Keys.{`package` => packageTask }
import com.typesafe.sbt.osgi.{OsgiKeys, SbtOsgi}

// plugin logic of build based on https://github.com/retronym/boxer

lazy val commonSettings = scalaModuleSettings ++ Seq(
  repoName                   := "scala-continuations",
  organization               := "org.scala-lang.plugins",
  version                    := "1.0.3-SNAPSHOT",
  scalaVersion               := crossScalaVersions.value.head,
  crossScalaVersions         := {
    val java = System.getProperty("java.version")
    if (java.startsWith("1.6.") || java.startsWith("1.7."))
      Seq("2.11.8")
    else if (java.startsWith("1.8.") || java.startsWith("1.9."))
      Seq("2.12.0")
    else
      sys.error(s"don't know what Scala versions to build on $java")
  },
  snapshotScalaBinaryVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature")
) ++ crossVersionSharedSources

lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc ).value.map { dir: File =>
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, y)) if y == 11 => new File(dir.getPath + "-2.11")
          case Some((2, y)) if y == 12 => new File(dir.getPath + "-2.12")
        }
      }
    }
  }

lazy val root = project.in( file(".") ).settings( publishArtifact := false ).aggregate(plugin, library).settings(commonSettings : _*)

lazy val plugin = project settings (scalaModuleOsgiSettings: _*) settings (
  name                   := "scala-continuations-plugin",
  crossVersion           := CrossVersion.full, // because compiler api is not binary compatible
  libraryDependencies    += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  OsgiKeys.exportPackage := Seq(s"scala.tools.selectivecps;version=${version.value}")
) settings (commonSettings : _*)

val pluginJar = packageTask in (plugin, Compile)

// TODO: the library project's test are really plugin tests, but we first need that jar
lazy val library = project settings (scalaModuleOsgiSettings: _*) settings (MimaPlugin.mimaDefaultSettings: _*) settings (
  name                       := "scala-continuations-library",
  MimaKeys.mimaPreviousArtifacts  := Set(
    organization.value % s"${name.value}_2.11" % "1.0.2"
  ),
  scalacOptions       ++= Seq(
    // add the plugin to the compiler
    s"-Xplugin:${pluginJar.value.getAbsolutePath}",
    // enable the plugin
    "-P:continuations:enable",
    // add plugin timestamp to compiler options to trigger recompile of
    // the library after editing the plugin. (Otherwise a 'clean' is needed.)
    s"-Jdummy=${pluginJar.value.lastModified}"),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler"  % scalaVersion.value % "test",
    "junit"          % "junit"           % "4.11" % "test",
    "com.novocode"   % "junit-interface" % "0.10" % "test"),
  testOptions          += Tests.Argument(
    TestFrameworks.JUnit,
    s"-Dscala-continuations-plugin.jar=${pluginJar.value.getAbsolutePath}"
  ),
  // run mima during tests
  test in Test := {
    MimaKeys.mimaReportBinaryIssues.value
    (test in Test).value
  },
  OsgiKeys.exportPackage := Seq(s"scala.util.continuations;version=${version.value}")
) settings (commonSettings : _*)
