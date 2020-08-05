object july28 {
  def init(length: Int): Array[Int] = {
    val xs = new Array[Int](length)
    for(i <- 0 until length) { xs(i) = i }
    xs
  }                                               //> init: (length: Int)Array[Int]
  val parOps = ParOps(Threshold(3))               //> parOps  : ParOps = ParOps@7c29daf3
   parOps.map(init(10))(_ + 1)                    //> res0: Array[Int] = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet

}
case class From(v: Int) extends AnyVal {
  def mid(end: End): Int = (this.v + end.v) / 2
}
case class End(v: Int) extends AnyVal
case class Threshold(v: Int) extends AnyVal {
  def reached(from: From, end: End): Boolean = end.v - from.v < this.v
}

object ReduceTree {
 import common._
 def apply(from: From, end: End, threshold: Threshold): ReduceTree =
  if(threshold.reached(from, end)) { Leaf(from.v, end.v) }
  else {
    val mid = from mid end
    val (left, right) = parallel(apply(from, End(mid), threshold), apply(From(mid), end, threshold))
    Node(left, right)
  }
}
sealed trait ReduceTree {
  import common._
  def reduce[A](compute: Leaf => A)(combine: (A, A) => A): A =  {
    def reduceTree(tree: ReduceTree): A = tree match {
      case leaf @ Leaf(_, _) => compute(leaf)
      case Node(left, right) =>
        val (lr, rr) = parallel(reduceTree(left), reduceTree(right))
        combine(lr, rr)
    }
    reduceTree(this)
  }
}
case class Leaf(from: Int, end: Int) extends ReduceTree
case class Node(left: ReduceTree, right: ReduceTree) extends ReduceTree
object ParOps {
  def apply(threshold: Threshold): ParOps = new ParOps(threshold)
  
}
class ParOps private(threshold: Threshold) {
  def reduceSeg[A](in: Array[A])(f: (A, A) => A): Leaf => A = leaf => {
    in.slice(leaf.from, leaf.end).reduce(f)
  }
  val buildTree: (From, End) => ReduceTree = ReduceTree(_, _, threshold)
  def reduce[A](inp: Array[A])(f: (A, A) => A): A = {
    val tree = buildTree(From(0), End(inp.length))
    val compute = reduceSeg(inp)(f)
    val combine = f
    tree.reduce(compute)(combine)
  }
  def foldLeftSegment[A, B](inp: Array[A], unit: B)(f:(B, A) => B): Leaf => B =
    leaf => {
      inp.slice(leaf.from, leaf.end).foldLeft(unit)(f)
   }
   def map[A, B: Manifest](inp: Array[A])(f: A => B): Array[B] = {
     val result = new Array[B](inp.length)
     val tree = buildTree(From(0), End(inp.length))
     val compute: Leaf => Unit = leaf => {
       for(i <- leaf.from until leaf.end) { result(i) = f(inp(i))}
     }
     tree.reduce(compute)((x, _) => x)
     result
   }
}
import java.util.concurrent._
import scala.util.DynamicVariable

 object common {

  val forkJoinPool = new ForkJoinPool

  abstract class TaskScheduler {
    def schedule[T](body: => T): ForkJoinTask[T]
    def parallel[A, B](taskA: => A, taskB: => B): (A, B) = {
      val right = task {
        taskB
      }
      val left = taskA
      (left, right.join())
    }
  }

  class DefaultTaskScheduler extends TaskScheduler {
    def schedule[T](body: => T): ForkJoinTask[T] = {
      val t = new RecursiveTask[T] {
        def compute = body
      }
      forkJoinPool.execute(t)
      t
    }
  }

  val scheduler =
    new DynamicVariable[TaskScheduler](new DefaultTaskScheduler)

  def task[T](body: => T): ForkJoinTask[T] = {
    scheduler.value.schedule(body)
  }

  def parallel[A, B](taskA: => A, taskB: => B): (A, B) = {
    scheduler.value.parallel(taskA, taskB)
  }

  def parallel[A, B, C, D](taskA: => A, taskB: => B, taskC: => C, taskD: => D): (A, B, C, D) = {
    val ta = task { taskA }
    val tb = task { taskB }
    val tc = task { taskC }
    val td = taskD
    (ta.join(), tb.join(), tc.join(), td)
  }

}
















object Change {

  
  def changeRecursive(money: Int, coins: List[Int]): Int = (money, coins) match {
      case (0, _) => 1
      case (x, _) if x < 0 => 0
      case (_, Nil) => 0
      case (x, List(coin)) if x % coin == 0 => 1
      case (x, c :: cs) =>
        changeRecursive(money - c, coins) + changeRecursive(money, cs)
  }
}