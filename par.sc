object par {
   var tree = Tree.withMaxSize(1).add(0).add(1)   //> tree  : Tree#40104[Int#1087] = Node(Leaf(List(0),0,0),Leaf(List(1),0,1))
   for(i <- 2 to 15) {
     tree = tree add i
   }
   tree                                           //> res0: Tree#40104[Int#1087] = Node(Node(Leaf(List(0),0,0),Leaf(List(1),0,1)),
                                                  //| Node(Leaf(List(2),0,2),Node(Leaf(List(3),0,3),Node(Leaf(List(4),0,4),Leaf(Li
                                                  //| st(5),0,5)))))
 }


object Tree {
  def apply[A](a: A): Tree[A] = Leaf(List(a), 512, 0)
  def withMaxSize[A](n: Int): Tree[A] = Leaf(List.empty[A], n, 0)
}
sealed trait Tree[A] {
  import common._
  
  def add(a: A): Tree[A]
  def idx: Long = this match {
    case Leaf(_, _, id) => id
    case Node(left, right) =>
     val (li, ri) = parallel(left.idx, right.idx)
     Math.max(li, ri)
  }
  def foreach(f: A => Unit): Unit = this match {
    case Leaf(data, _, _) => data foreach f
    case Node(left, right) =>
      left foreach f
      right foreach f
  }
  def height: Int = this match {
    case Leaf(_, _, _) => 0
    case node @ Node(_, _) if !node.isGrandParent => 1
    case Node(left, right) =>
      val (ld, rd) = parallel(left.height, right.height)
      1 + ld + rd
  }
  def insert(leaf: Leaf[A]): Tree[A] = this match {
    case bottom @ Leaf(_, _, id) =>
     if(id < leaf.id) Node(bottom, leaf)
     else Node(leaf, bottom)
    case node @ Node(left, right) =>
      if(left.idx < leaf.id) Node(left, right insert leaf)
      else Node(left insert leaf, right)
  }
  def remove(leaf: Leaf[A]): Tree[A] = this match {
    case leaf @ Leaf(_, _, _) => leaf
    case Node(left, right) if(leaf == left) => right
    case Node(left, right) if(left.idx < leaf.id) => Node(left, right remove leaf)
    case Node(left, right) => Node(left remove leaf, right)
  }
}
case class Leaf[A](data: List[A], maxSize: Int, id: Long) extends Tree[A] {
  def add(a: A): Tree[A] = if(maxSize != 0) {
    Leaf(data :+ a, maxSize - 1, id)
  } else {
   val right = Leaf(List.empty[A], data.size, id + 1)
   Node(this, right add a)
 }
}
case class Node[A](left: Tree[A], right: Tree[A]) extends Tree[A] {
  import common._
  def isGrandParent: Boolean = left.isInstanceOf[Node[_]] || right.isInstanceOf[Node[_]]
  def add(a: A): Tree[A] = {
  var tmp = this
  while(disbalanced(tmp)) {
    tmp  = rotate(tmp)
  }
  Node(tmp.left, tmp.right add a)
 }
  def disbalanced(node: Node[A]) = node.right.height - node.left.height > 2
  def rotate(node: Node[A]): Node[A] = {
    node.right match {
      case Node(leaf @ Leaf(_, _, _), _) =>
        val (l, r) = parallel(left insert leaf, right remove leaf)
        Node(l, r)
    }
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