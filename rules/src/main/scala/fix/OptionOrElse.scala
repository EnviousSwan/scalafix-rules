package fix

import scalafix.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scala.meta.Case
import scala.meta.Pat
import scala.meta.Term
import scala.meta.Term.Block
import scala.meta.tokens.Token

class OptionOrElse extends SyntacticRule("OptionOrElse") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Match(
            expr,
            List(
              Case(
                Pat.Extract(Term.Name("Some"), Pat.Var(Term.Name(a1)) :: Nil),
                None,
                Term.Apply(Term.Name("Some"), Term.Name(a2) :: Nil)
              ),
              Case(
                Pat.Wildcard() | Term.Name("None"),
                None,
                alternative
              )
            )
          ) if a1 == a2 =>
        val (open, close) = {
          alternative match {
            case Block(stats) if stats.size > 1 && !alternative.tokens.forall(_.is[Token.LeftBrace]) =>
              "{" -> "}"
            case _ =>
              "(" -> ")"
          }
        }
        Patch.replaceTree(t, s"${expr}.orElse${open}${alternative}${close}")
    }.asPatch
  }
}
