import akka.actor._
import akka.routing.RoundRobinPool
import scala.concurrent.duration._
import java.security.MessageDigest
import scala.collection.mutable.ArrayBuffer
import com.typesafe.config.ConfigFactory

//Messages
sealed trait BitCoinMessage
case object RemoteNotify extends BitCoinMessage
case object terminate
case class terminate(duration: Long)
case class Mine(noOfZeroes: Int, coinCollector: ActorRef) extends BitCoinMessage
case class Work(noOfZeroes: Int, passingStr: String, coinCollector: ActorRef) extends BitCoinMessage
case class Result(value: String) extends BitCoinMessage
case class SendBitCoin(value: String) extends BitCoinMessage
case class AllocateWork(NoOfZeroes:Int, coinCollector: ActorRef) extends BitCoinMessage
case class CollectCoins(coinHash: String)

object BitCoinMinerRemote {
  
  def main(args: Array[String]) {
    
    val configfactory = ConfigFactory.parseString(
    """
    akka {
       actor {
           provider = "akka.remote.RemoteActorRefProvider"
             }
       remote {
           transport = ["akka.remote.netty.tcp"]
       netty.tcp {
           hostname = "127.0.0.1"
           port = 0
                 }
             }
        }
   """)
   
   val ipAddress: String = args(0)
   
   val processors = Runtime.getRuntime().availableProcessors()
   println("processors::"+processors)
   val noOfWorkers : Int = 8//processors
   val noOfZeroes : Int = 0 
   
   val remoteSystem = ActorSystem("BitCoinMinerRemote", ConfigFactory.load(configfactory))
   
   val master = remoteSystem.actorOf(Props(new Master(noOfWorkers,noOfZeroes)),name = "master")
   val notifyServerActor = remoteSystem.actorOf(Props(new NotifyServerActor(args(0),master)), name = "NotifyServerActor") 
    
   //get the work from the server
   notifyServerActor ! "GetWorkFromServer"  
  }
  
  class NotifyServerActor(ip: String , master:ActorRef) extends Actor {
 
  val server = context.actorSelection("akka.tcp://BitCoinMiningSource@" + ip + ":1245/user/master")

  def receive = {
    case "GetWorkFromServer" =>
      println("Assigning Work to Remote Worker")
       server ! "AssignWorkToMe"
    case AllocateWork(noOfZeroes, coinCollector) =>
       println("Received Work from server to generate Bitcoins which start with " + noOfZeroes +" Zeroes")
       master ! Mine(noOfZeroes, coinCollector)
  }
}
  
  class Master(noOfWorkers: Int, noOfZeroes: Int)
    extends Actor {
 
    println("Entered Master")
    val start: Long = System.currentTimeMillis
    var bitCoinCount: Int = 0
    
    val passingStr: String = "ktarun90"
 
    val workerRouter = context.actorOf(Props[Worker].withRouter(RoundRobinPool(noOfWorkers)), name = "workerRouter")
 
    def receive = {
      case Mine(noOfZeroes, coinCollector) ⇒
         workerRouter ! Work(noOfZeroes, passingStr,coinCollector)          
    }
  }
  
  class Worker extends Actor {
    
    println("--------Remote Worker started----------")
    var nonceCount : Int = 0
 
    def receive = {
      case Work(noOfZeroes, passingStr,coinCollector) ⇒
        findBitCoins(noOfZeroes, passingStr,coinCollector)
    }
    
    def randomAlphaNumericString(length: Int): String = {
      val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
      randomStringFromCharList(length, chars)
    }
    
    def randomStringFromCharList(length: Int, chars: Seq[Char]): String = {
      val sb = new StringBuilder
      for (i <- 1 to length) {
        val randomNum = util.Random.nextInt(chars.length)
        sb.append(chars(randomNum))
      }
      sb.toString
    }
    
    def findBitCoins(noOfZeroes: Int, passingStr: String, coinCollector: ActorRef) = 
    {
      var coinHash : String = ""
      var validBitCoin : String = ""
     
      while(true) 
      {
        coinHash = generateHashInput(passingStr, noOfZeroes)
        if(coinHash != "-1")
          coinCollector ! SendBitCoin(coinHash)
      } 
    }
    
    def generateHashInput (passingStr: String, noOfZeroes: Integer): String=
    {  
      var nonce = randomAlphaNumericString(6) 
      nonceCount += 1
      var hashingStr: String = passingStr + nonce + nonceCount
      var hashProduced = sha256Method(hashingStr)
      var coinHash: String = ""
      
      coinHash = checkValidCoin(hashingStr, hashProduced, noOfZeroes)
      coinHash
    }
    
    def checkValidCoin (hashingStr: String, hashProduced: String, noOfZeroes: Integer): String=
    {
      var compareStr = ""
      var coinHash : String = ""
      
       for (i ← 0 until noOfZeroes)
          compareStr = compareStr + "0"
                      
        if (hashProduced.startsWith(compareStr))
          coinHash = hashingStr+'|'+hashProduced
        else
          coinHash = "-1"
      coinHash
    }
    
    def sha256Method (input:String): String =
    {
      val md = MessageDigest.getInstance("SHA-256")
      val hexString= md.digest(input.getBytes("UTF-8")).map("%02x".format(_)).mkString
      return hexString
    }
  }
}