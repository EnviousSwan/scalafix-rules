package fix

import scalafix.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scala.meta.Case
import scala.meta.Lit
import scala.meta.Pat
import scala.meta.Term
import scala.meta.Term.ApplyInfix

/**
 * [[https://github.com/scala/scala/blob/v2.13.10/src/library/scala/Option.scala#L367-L373]]
 */
class OptionContains extends SyntacticRule("OptionContains") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Match(
            expr,
            List(
              Case(
                Pat.Extract(Term.Name("Some"), Pat.Var(Term.Name(a1)) :: Nil),
                None,
                body
              ),
              Case(
                Pat.Wildcard() | Term.Name("None"),
                None,
                Lit.Boolean(false)
              )
            )
          ) =>
        PartialFunction
          .condOpt(body) {
            case ApplyInfix(Term.Name(a2), Term.Name("=="), Nil, b :: Nil) if a1 == a2 =>
              b
            case ApplyInfix(b, Term.Name("=="), Nil, Term.Name(a2) :: Nil) if a1 == a2 =>
              b
          }
          .map { b =>
            Patch.replaceTree(t, s"${expr}.contains(${b})")
          }
          .asPatch
    }.asPatch
  }
}
