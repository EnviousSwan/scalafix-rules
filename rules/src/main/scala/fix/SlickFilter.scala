package fix

import scala.meta.Case
import scala.meta.Lit
import scala.meta.Pat
import scala.meta.Term
import scala.meta.Tree
import scala.meta.Type
import scala.meta.Term.ApplyInfix
import scala.meta.Term.Block
import scalafix.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule

object SlickFilter {
  private object CaseSome {
    def unapply(c: Case): Option[(String, Term)] = PartialFunction.condOpt(c) {
      case Case(
            Pat.Extract(
              Term.Name("Some"),
              Pat.Var(Term.Name(a)) :: Nil
            ),
            None,
            body
          ) =>
        (a, body)
    }
  }

  private object TrueRepBoolean {
    def unapply(x: Term): Boolean = PartialFunction.cond(x) {
      case Term.Ascribe(
            Lit.Boolean(true),
            Type.Apply(
              Type.Name("Rep"),
              Type.Name("Boolean") :: Nil
            )
          ) =>
        true
    }
  }

  private object CaseNone {
    def unapply(c: Case): Boolean = PartialFunction.cond(c) {
      case Case(
            Term.Name("None") | Pat.Wildcard(),
            None,
            TrueRepBoolean()
          ) =>
        true
    }
  }

  private object ReplaceFilterOpt {
    def unapply(x: Tree): Option[ReplaceFilterOpt] = PartialFunction.condOpt(x) {
      case Term.Match(expr, CaseSome(s, body) :: CaseNone() :: Nil) =>
        ReplaceFilterOpt(expr, s, body)
      case Term.Match(expr, CaseNone() :: CaseSome(s, body) :: Nil) =>
        ReplaceFilterOpt(expr, s, body)
    }
  }

  case class ReplaceFilterOpt private (matchExpr: Term, paramName: String, body: Term)

  private object ReplaceFilterIf {
    def unapply(x: Tree): Option[ReplaceFilterIf] = PartialFunction.condOpt(x) {
      case Term.If(cond, TrueRepBoolean(), expr) =>
        ReplaceFilterIf(cond, expr, true)
      case Term.If(cond, Block(TrueRepBoolean() :: Nil), expr) =>
        ReplaceFilterIf(cond, expr, true)
      case Term.If(cond, expr, TrueRepBoolean()) =>
        ReplaceFilterIf(cond, expr, false)
      case Term.If(cond, expr, Block(TrueRepBoolean() :: Nil)) =>
        ReplaceFilterIf(cond, expr, false)
    }
  }

  case class ReplaceFilterIf private (cond: Term, expr: Term, thenIsTrue: Boolean)

  private object InfixAndValues {
    def unapply(x: Term): Option[List[Term]] = {
      PartialFunction.condOpt(x) {
        case ApplyInfix(left, Term.Name("&&"), Nil, right :: Nil) =>
          unapply(left).toList.flatten ++ unapply(right).toList.flatten
        case _ =>
          List(x)
      }
    }
  }

  object Func {
    def unapply(t: Term): Option[(String, Term, String, Boolean)] = PartialFunction.condOpt(t) {
      case Term.PartialFunction(Case(p1, None, x) :: Nil) =>
        (p1.toString, x, "case ", true)
      case Term.Block(Term.Function(p1 :: Nil, x) :: Nil) =>
        (p1.toString, x, "", true)
      case Term.Function(p1 :: Nil, x) =>
        (p1.toString, x, "", false)
    }
  }
}

class SlickFilter extends SyntacticRule("SlickFilter") {
  import SlickFilter._

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Apply(
            Term.Select(obj, Term.Name("filter")),
            Func(p1, InfixAndValues(values), caseOpt, block) :: Nil
          ) if obj.collect {
            case ReplaceFilterIf(_) =>
              ()
            case ReplaceFilterOpt(_) =>
              ()
          }.isEmpty && values.collectFirst {
            case ReplaceFilterOpt(_) =>
              ()
            case ReplaceFilterIf(_) =>
              ()
          }.nonEmpty =>
        val (open, close) = {
          if (block) {
            ("{", "}")
          } else {
            ("(", ")")
          }
        }

        Patch.replaceTree(
          t,
          values.map {
            case ReplaceFilterOpt(x) =>
              s".filterOpt(${x.matchExpr}) { ${caseOpt}($p1, ${x.paramName}) => ${x.body} }"
            case ReplaceFilterIf(x) =>
              val unary = if (x.thenIsTrue) "!" else ""
              val cond0 = if (x.cond.toString.contains(" ") && x.thenIsTrue) s"(${x.cond})" else x.cond.toString
              val param = if (caseOpt.nonEmpty) s"($p1)" else p1
              s".filterIf(${unary}${cond0}) ${open} ${caseOpt}${param} => ${x.expr} ${close}"
            case x =>
              s".filter${open} ${caseOpt}${p1} => ${x} ${close}"
          }.mkString(obj.toString, "", "")
        )
    }.asPatch
  }
}
