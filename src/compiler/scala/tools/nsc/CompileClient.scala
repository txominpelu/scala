/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$

package scala.tools.nsc

import java.lang.System.getProperty
import java.io.{BufferedReader, File, InputStreamReader, PrintWriter}

import scala.compat.StringBuilder
import scala.tools.util.StringOps

/** The main class for NSC, a compiler for the programming
 *  language Scala.
 */
object CompileClient {
  val PRODUCT: String = getProperty("scala.tool.name", "scalac")
  val VERSION: String = getProperty("scala.tool.version", "unknown version")
  val COPYRIGHT: String = getProperty("scala.copyright", "(c) 2002-2006 LAMP/EPFL")

  val versionMsg = PRODUCT + " " + VERSION + " -- " + COPYRIGHT

  var verbose = false
  var version = false

  /** Convert a filename to an absolute path */
  def absFileName(path: String) = new File(path).getAbsolutePath()

  /** Convert a sequence of filenames, separated by <code>File.pathSeparator</code>,
    * into absolute filenames.
    */
  def absFileNames(paths: String) = {
    val sep = File.pathSeparator
    val pathsList = paths.split(sep).toList
    pathsList.map(absFileName).mkString("", sep, "")
  }

  private def normalize(args: Array[String]): Pair[String, String] = {
    var i = 0
    val vmArgs = new StringBuilder
    var serverAdr = ""
    while (i < args.length) {
      val arg = args(i)
      if (arg endsWith ".scala") {
        args(i) = absFileName(arg)
      } else if (arg startsWith "-J") {
        //see http://java.sun.com/j2se/1.5.0/docs/tooldocs/solaris/javac.html#J
        vmArgs append " "+arg.substring(2)
        args(i) = ""
      } else if (arg == "-verbose") {
        verbose = true
      } else if (arg == "-version") {
        version = true
      }
      i = i + 1
      if (i < args.length) {
        if (arg == "-classpath" ||
            arg == "-sourcepath" ||
            arg == "-bootclasspath" ||
            arg == "-extdirs" ||
            arg == "-d") {
          args(i) = absFileNames(args(i))
          i = i + 1
        } else if (arg == "-server") {
          serverAdr = args(i)
          args(i-1) = ""
          args(i) = ""
        }
      }
    }
    Pair(vmArgs.toString, serverAdr)
  }

  // used by class ant.FastScalac to skip exit statement in Ant.
  def main0(args0: Array[String]): Int = {
    val args =
      if (args0.exists(arg => arg == "-d"))
        args0
      else
        ("-d" :: "." :: args0.toList).toArray

    val Pair(vmArgs, serverAdr) = normalize(args)
    if (version) {
      Console.println(versionMsg)
      return 0
    }
    if (verbose) {
      Console.println("[Server arguments: " + args.mkString("", " ", "]"))
      Console.println("[VM arguments: " + vmArgs + "]")
    }
    val socket = if (serverAdr == "") CompileSocket.getOrCreateSocket(vmArgs)
                 else CompileSocket.getSocket(serverAdr)
    val out = new PrintWriter(socket.getOutputStream(), true)
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
    out.println(CompileSocket.getPassword(socket.getPort()))
    out.println(args.mkString("", "\0", ""))
    var sawerror = false
    var fromServer = in.readLine()
    while (fromServer != null) {
      if (CompileSocket.errorPattern.matcher(fromServer).matches)
        sawerror = true
      Console.println(fromServer)
      fromServer = in.readLine()
    }
    in.close()
    out.close()
    socket.close()

    if (sawerror) 1 else 0
  }

  def main(args: Array[String]) {
    val status = main0(args)
    exit(status)
  }
}
