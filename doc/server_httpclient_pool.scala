package example

import zio.ZIO
import zio.ZEnv
import zhttp._
import zhttp.dsl._

import java.time.ZonedDateTime

import zio.blocking._
import zhttp.HttpRoutes.WebFilterProc
import Method._

import zio.json._
import zio.Chunk
import zhttp.clients._
import MyLogging.MyLogging
import clients.ResPool.ResPool
import clients.HttpConnection




object myServer extends zio.App {


  val ROOT_CATALOG = "/app/web_root"


  //////////////////////////////////
  def run(args: List[String]) = {


    val app_route_JSON = HttpRoutes.of { 


       case GET -> Root / "pool" =>
       for {
              con <- ResPool.acquire[HttpConnection]
 
              response <- con.send( clients.ClientRequest( GET, "/test"  ).hdr( "MyHeader" -> "Happy Holidays!") )

              _  <- ResPool.release( con )

              str <- ZIO( new String( "CODE " + response.code + "\n" + response.hdrs.printHeaders + "\n" + 
                                         response.asText + " " + "keep = " + response.isKeepAlive ) )
              

       } yield( Response.Ok.asTextBody( str ) ) 
      }

  
    type MyEnv = MyLogging with ResPool[HttpConnection]  

    val myHttp = new TLSServer[MyEnv]
    val myHttpRouter = new HttpRouter[MyEnv]

    //app routes
    myHttpRouter.addAppRoute( app_route_JSON )

    //server
    myHttp.KEYSTORE_PATH = "keystore.jks"
    myHttp.KEYSTORE_PASSWORD = "password"
    myHttp.TLS_PROTO = "TLSv1.2"         //default TLSv1.2 in JDK8
    myHttp.BINDING_SERVER_IP = "0.0.0.0" //make sure certificate has that IP on SAN's list
    myHttp.KEEP_ALIVE = 2000             //ms, good if short for testing with broken site's snaphosts with 404 pages
    myHttp.SERVER_PORT = 8111

    ResPool.TIME_TO_LIVE = 1800   // !!! must be less then keep alive on the server !!!

    val http_client_pool_L   = ResPool.makeM[HttpConnection](
        () => HttpConnection.connect( "https://localhost:443", "keystore.jks", "password" ),
        _.close ) 

    myHttp
      .run(myHttpRouter.route)
      .provideSomeLayer[ZEnv with MyLogging]( http_client_pool_L )
      .provideSomeLayer[ZEnv](MyLogging.make(("console" -> LogLevel.Trace), ("client" -> LogLevel.Trace), ("access" -> LogLevel.Info )))
      .exitCode
  }
}
