package Script

/** BUILD INSTRUCTIONS

mkdir META-INF

cat >META-INF/MANIFEST.MF <<EOL
Manifest-Version: 1.0
Class-Path: lib/scala-library.jar lib/commons-csv-1.1.jar
Built-By: James.Leslie
Build-Jdk: 1.7.0_51
Main-Class: Script.SunoreDocCheck 
EOL

scalac -cp lib/commons-csv-1.1.jar SunoreDocCheck.scala
zip -r SunoreDocCheck.jar META-INF SunoreDocCheck

java -jar SunoreDocCheck.jar chapter

*/
  object DocChecker {
    import java.io.{FileWriter, File}
    import scala.io.Source.fromFile
    import scala.collection.JavaConverters._
    import scala.collection.JavaConversions._
    import scala.language.implicitConversions
    import org.apache.commons.csv.{CSVParser, CSVPrinter, CSVFormat}

    def main(args: Array[String]) {
      //check param is valid dir
      val badArgMsg = "You must pass the path to a valid directory containing tex files."
      if (args.size == 0)            { println(badArgMsg); return }
      if (!new File(args(0)).isDirectory) { println(args(0) +" is not a path to a valid directory. "+  badArgMsg); return }
      println("\nNote lines are checked in isolation. So matches spanning multiple lines will not be found.\n")
      println("STARTING...\n")
      //parse csv
      val regData = CSVParser.parse(fromFile("config.csv").mkString, CSVFormat.EXCEL.withIgnoreSurroundingSpaces()).iterator.toVector.drop(1).map(_.toList)
      //csv data checks
      val badData = regData.zipWithIndex.filter(_._1.length != 5)
      if(! badData.isEmpty) badData.foreach{case (x,i) => println(s"Ignoring Bad Entry: $i width is ${x.size} expected 5.\n$x")}
      println(regData.filter(_.head != "").mkString("\nDisabled Rows\n","\n","\n---\n"))
      //transform csv
      val regexes = for( List(inUse, mType, transform, regexStr, errorMessage) <- regData if inUse == "") yield {
        val regex = transform match {
          case "findWords" => s"(?i)\\b($regexStr)\\b".r
          case ""          => regexStr.r
          case other       => println("Ignoring Bad Transform: "+ other); regexStr.r
        }
        (mType,regex,errorMessage.trim)
      }
      //process document dir looking for error cases
      val header = Vector("FILE", "LINE", "POS", "TYPE", "PATTERN", "MESSAGE", "MATCH", "LINE STRING")
      val errors =  for (   file                   <- new File(args(0)).listFiles().filter(_.getName endsWith ".tex");
                           (line, lineNum)         <- fromFile(file).getLines().zipWithIndex;
                           (matchType, regex, msg) <- regexes                               ;
                            mch                    <- regex.findAllMatchIn(line)            ) yield {
        Vector[String](file.getName, lineNum.toString, mch.start.toString, matchType, regex.toString(), msg, mch.matched, line)
      }
      //write output csv
      val fileWriter = new FileWriter("SunoreDocCheckOutput.csv")
      val csvFilePrinter = new CSVPrinter(fileWriter, CSVFormat.EXCEL)
      try    { (header +: errors).foreach(x => csvFilePrinter.printRecord(x.asJava)) }
      catch  { case e:Exception => println("Error in CsvFileWriter !!!")               }
      finally{ fileWriter.flush(); fileWriter.close(); csvFilePrinter.close()          }
      println("DONE")
    }
  }
