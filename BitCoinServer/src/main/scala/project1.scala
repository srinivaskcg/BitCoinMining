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

object BitCoinMiningSource {
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
           port = 1245
                 }
             }
        }
   """)
   
   val sourceSystem = ActorSystem("BitCoinMiningSource", ConfigFactory.load(configfactory))
   val processors = Runtime.getRuntime().availableProcessors()
   var startTime = System.currentTimeMillis()
   //println("processors::"+processors)
   
   //Reading input for number of zeroes
   val noOfZeroes: Int = args(0).toInt
   val noOfWorkers : Int = 8 //processors
   val start: Long = System.currentTimeMillis
   
   if(noOfZeroes.isValidInt && noOfZeroes>0)
     {
       //start mining with the required no of workers
       println("Bitcoin Mining Starts has started!!!!!!!!!!!")
       
       val coinCollector = sourceSystem.actorOf(Props[CoinRepository], name = "coinCollector")
       val master = sourceSystem.actorOf(Props(new Master(noOfWorkers, noOfZeroes,coinCollector)),name = "master")
       master ! Mine
       
       /*if((System.currentTimeMillis()-start)>300000)
       {
         println("Bit Coin Mining Completed")
         sourceSystem.stop(master)
       }*/
       Thread.sleep(300000)
       println("Bitcoin Mining Completed.")
       coinCollector ! PoisonPill
     }
   else
     {
       println("Please enter a valid value for number of zeroes")
       System.exit(0)
     }
  }
  
  class Master(noOfWorkers: Int, noOfZeroes: Int, coinCollector: ActorRef)
    extends Actor {
 
    println("---------Master started----------")
    
    val passingStr: String = "sgubbala"
    val start: Long = System.currentTimeMillis
 
    val workerRouter = context.actorOf(Props[Worker].withRouter(RoundRobinPool(noOfWorkers)), name = "workerRouter")
   
    def receive = {
      case Mine ⇒
         workerRouter ! Work(noOfZeroes, passingStr, coinCollector) 
           
       case "AssignWorkToMe" => {
          sender ! AllocateWork(noOfZeroes,coinCollector)   
    }
    }
  }
}
  
  class Worker extends Actor {
    
    println("--------Local Worker started----------")
    var nonceCount : Int = 0
 
    def receive = {
      case Work(noOfZeroes, passingStr, coinCollector) ⇒
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
    
    def findBitCoins(noOfZeroes: Int, passingStr: String,coinCollector: ActorRef) = 
    {
      var coinHash : String = ""
      var validBitCoin : String = ""
     
      //for(i <- 1 to 1000000)
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
  
  class CoinRepository extends Actor {
    
    var bitCoinCount = 0
    
    def receive = {
      case SendBitCoin(bitCoin) ⇒
          var splitStrings: Array[String]  = new Array[String] (2)
          splitStrings = bitCoin.split('|')
            
          var input: String = splitStrings(0)
          var hash : String = splitStrings(1)
    
          println(input+"\t"+hash)
          bitCoinCount += 1
          println("Current BitCoinCount:"+bitCoinCount)
      
          
    }
}