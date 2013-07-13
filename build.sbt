name := "netty"

organization := "net.liftweb"

version := "1.0"

scalaVersion := "2.10.0"

scalacOptions ++= Seq(
  "-unchecked", 
  "-deprecation", 
  "-feature", 
  "-language:reflectiveCalls", 
  "-language:postfixOps"
)

seq(sbt.dist.Dist.distSettings: _*)

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Twitter's Repository" at "http://maven.twttr.com/",
  "Maven Repo" at "http://repo1.maven.org/maven2/",
  "Typesafe Ivy Repo" at "http://repo.typesafe.com/typesafe/ivy-releases",
  "Typesafe Maven Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"
)

libraryDependencies ++= Seq(
  "net.liftweb"         %%  "lift-webkit"       % "2.5-M4"            % "compile",
  //"org.eclipse.jetty"   %   "jetty-webapp"      % "7.6.11.v20130520",
  "ch.qos.logback"      %   "logback-classic"   % "1.0.0"             % "compile",
  "io.netty"            %   "netty-all"         % "4.0.0.CR9"
)

// to put webapp content to root of jar
resourceGenerators in Compile <+= (resourceManaged, baseDirectory) map
  { (managedBase, base) =>
    val webappBase = base / "src" / "main" / "webapp"
    for {
      (from, to) <- webappBase ** "*" x rebase(webappBase, managedBase / "main")
    } yield {
      Sync.copy(from, to)
      to
    }
  }

// to make ~ working on webapp
watchSources <++= baseDirectory map { base =>
  val webappBase = base / "src" / "main" / "webapp"
  (webappBase ** "*").get
}