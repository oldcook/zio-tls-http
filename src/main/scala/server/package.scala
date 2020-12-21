import zhttp.MyLogging.MyLogging
import zhttp.clients._
import zhttp.clients.ResPoolGroup.ResPoolGroup

package object zhttp 
{

    type MyEnv = MyLogging with ResPoolGroup[HttpConnection]

}