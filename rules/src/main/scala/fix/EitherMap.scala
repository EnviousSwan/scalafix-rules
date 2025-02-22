package fix

import scalafix.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scala.meta.Case
import scala.meta.Pat
import scala.meta.Term

class EitherMap extends SyntacticRule("EitherMap") {
  private object RightMapIdentity {
    def unapply(c: Case): Boolean = PartialFunction.cond(c) {
      case Case(
            Pat.Extract(Term.Name("Right"), a1 :: Nil),
            None,
            Term.Apply(Term.Name("Right"), a2 :: Nil)
          ) if a1.toString == a2.toString =>
        true
    }
  }

  private object LeftMapIdentity {
    def unapply(c: Case): Boolean = PartialFunction.cond(c) {
      case Case(
            Pat.Extract(Term.Name("Left"), a1 :: Nil),
            None,
            Term.Apply(Term.Name("Left"), a2 :: Nil)
          ) if a1.toString == a2.toString =>
        true
    }
  }

  private object LeftToLeft {
    def unapply(c: Case): Option[(Term.Name, Term)] = PartialFunction.condOpt(c) {
      case Case(
            Pat.Extract(Term.Name("Left"), Pat.Var(a1) :: Nil),
            None,
            Term.Apply(Term.Name("Left"), arg :: Nil)
          ) =>
        (a1, arg)
    }
  }

  private object RightToRight {
    def unapply(c: Case): Option[(Term.Name, Term)] = PartialFunction.condOpt(c) {
      case Case(
            Pat.Extract(Term.Name("Right"), Pat.Var(a1) :: Nil),
            None,
            Term.Apply(Term.Name("Right"), arg :: Nil)
          ) =>
        (a1, arg)
    }
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Match(expr, RightMapIdentity() :: LeftToLeft(arg, fun) :: Nil) =>
        Patch.replaceTree(t, s"${expr}.left.map($arg => $fun)")
      case t @ Term.Match(expr, LeftToLeft(arg, fun) :: RightMapIdentity() :: Nil) =>
        Patch.replaceTree(t, s"${expr}.left.map($arg => $fun)")
      case t @ Term.Match(expr, LeftMapIdentity() :: RightToRight(arg, fun) :: Nil) =>
        Patch.replaceTree(t, s"${expr}.map($arg => $fun)")
      case t @ Term.Match(expr, RightToRight(arg, fun) :: LeftMapIdentity() :: Nil) =>
        Patch.replaceTree(t, s"${expr}.map($arg => $fun)")
    }
  }.asPatch
}
