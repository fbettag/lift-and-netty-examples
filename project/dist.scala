package sbt.dist

import sbt._
import Keys._

object Dist {
	val packageDistReleaseBuild =
    SettingKey[Boolean]("package-dist-release-build", "is this a release build")

  /**
   * where to build and stick the dist
   */
  val packageDistDir =
    SettingKey[File]("package-dist-dir", "the directory to package dists into")

  /**
   * the task to actually build the zip file
   */
  val packageDist =
    TaskKey[File]("package-dist", "package a distribution for the current project")

  /**
   * the name of our distribution
   */
  val packageDistName =
    SettingKey[String]("package-dist-name", "name of our distribution")


  /**
   * the name of our zip
   */
  val packageDistZipName =
    TaskKey[String]("package-dist-zip-name", "name of packaged zip file")

  /**
   * where to rebase files inside the zip
   */
  val packageDistZipPath =
    TaskKey[String]("package-dist-zip-path", "path of files inside the packaged zip file")

  /**
   * task to clean up the dist directory
   */
  val packageDistClean =
    TaskKey[Unit]("package-dist-clean", "clean distribution artifacts")


  /**
   * task to copy dependent jars from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyLibs =
    TaskKey[Set[File]]("package-dist-copy-libs", "copy scripts into the package dist folder")

  /**
   * task to copy exported jars from the target folder to dist
   */
  val packageDistCopyJars =
    TaskKey[Set[File]]("package-dist-copy-jars", "copy exported files into the package dist folder")

  /**
   * task to copy all dist-ready files to dist
   */
  val packageDistCopy =
    TaskKey[Set[File]]("package-dist-copy", "copy all dist files into the package dist folder")


  // utility to copy a directory tree to a new one
  def copyTree(
    srcOpt: Option[File],
    destOpt: Option[File],
    selectedFiles: Option[Set[File]] = None
  ): Set[(File, File)] = {
    srcOpt.flatMap { src =>
      destOpt.map { dest =>
        val rebaser = Path.rebase(src, dest)
        selectedFiles.getOrElse {
          (PathFinder(src) ***).filter(!_.isDirectory).get
        }.flatMap { f =>
          rebaser(f) map { rebased =>
            (f, rebased)
          }
        }
      }
    }.getOrElse(Seq()).toSet
  }


  val distSettings = Seq(
    exportJars := true,

    // write a classpath entry to the manifest
    packageOptions <+= (dependencyClasspath in Compile, mainClass in Compile) map { (cp, main) =>
      val manifestClasspath = cp.files.map(f => "libs/" + f.getName).mkString(" ")
      // not sure why, but Main-Class needs to be set explicitly here.
      val attrs = Seq(("Class-Path", manifestClasspath)) ++ main.map { ("Main-Class", _) }
      Package.ManifestAttributes(attrs: _*)
    },
    packageDistReleaseBuild <<= (version) { v => !(v.toString contains "SNAPSHOT") },
    packageDistName <<= (packageDistReleaseBuild, name, version) { (r, n, v) =>
      if (r) {
        n + "-" + v
      } else {
        n
      }
    },
    packageDistDir <<= (baseDirectory, packageDistName) { (b, n) => b / "dist" },

    // for releases, default to putting the zipfile contents inside a subfolder.
    packageDistZipPath <<= (
      packageDistReleaseBuild,
      packageDistName
    ) map { (release, name) =>
      if (release) name else ""
    },

    // to have more complex name in case we want to use git sha e.g
    packageDistZipName <<= (
      packageDistReleaseBuild,
      name,
      version
    ) map { (r, n, v) =>
      "%s-%s.zip".format(n, v)
    },

    packageDistCopyLibs <<= (
      dependencyClasspath in Runtime,
      exportedProducts in Compile,
      packageDistDir
    ) map { (cp, products, dest) =>
      val jarFiles = cp.files.filter(f => !products.files.contains(f))
      val jarDest = dest / "libs"
      jarDest.mkdirs()
      IO.copy(jarFiles.map { f => (f, jarDest / f.getName) })
    },

    // copy all our generated "products" (i.e. "the jars")
    packageDistCopyJars <<= (
      exportedProducts in Compile,
      packageDistDir
    ) map { (products, dest) =>
      IO.copy(products.files.map(p => (p, dest / p.getName)))
    },

    packageDistCopy <<= (
      packageDistCopyLibs,
      packageDistCopyJars
    ) map { (libs, jars) =>
      libs ++ jars
    },

    // package all the things
    packageDist <<= (
      baseDirectory,
      packageDistCopy,
      packageDistDir,
      packageDistName,
      packageDistZipPath,
      packageDistZipName,
      streams
    ) map { (base, files, dest, distName, zipPath, zipName, s) =>
      // build the zip
      s.log.info("Building %s from %d files.".format(zipName, (files).size))
      val zipRebaser = Path.rebase(dest, zipPath)
      val zipFile = base / "dist" / zipName
      IO.zip((files).map(f => (f, zipRebaser(f).get)), zipFile)
      zipFile
    }
  )

}