package fix

import scala.meta.Case
import scala.meta.Pat
import scala.meta.Term
import scalafix.Patch
import scalafix.v1.SyntacticDocument
import scalafix.v1.SyntacticRule
import scala.meta.inputs.Position
import scalafix.lint.Diagnostic
import scalafix.lint.LintSeverity

class OptionMapFlatMap extends SyntacticRule("OptionMapFlatMap") {
  private object CaseSome {
    def unapply(c: Case): Boolean = PartialFunction.cond(c) {
      case Case(
            Pat.Extract(Term.Name("Some"), Pat.Var(Term.Name(a1)) :: Nil),
            None,
            _
          ) =>
        true
    }
  }

  private object NoneToNone {
    def unapply(c: Case): Boolean = PartialFunction.cond(c) {
      case Case(
            Term.Name("None") | Pat.Wildcard(),
            None,
            Term.Name("None")
          ) =>
        true
    }
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Match(_, CaseSome() :: NoneToNone() :: Nil) =>
        Patch.lint(OptionMapFlatMapWarn(t.pos))
      case t @ Term.Match(_, NoneToNone() :: CaseSome() :: Nil) =>
        Patch.lint(OptionMapFlatMapWarn(t.pos))
      case t @ Term.PartialFunction(CaseSome() :: NoneToNone() :: Nil) =>
        Patch.lint(OptionMapFlatMapWarn(t.pos))
      case t @ Term.PartialFunction(NoneToNone() :: CaseSome() :: Nil) =>
        Patch.lint(OptionMapFlatMapWarn(t.pos))
    }.asPatch
  }
}

case class OptionMapFlatMapWarn(override val position: Position) extends Diagnostic {
  override def message = "maybe you can use Option#map or flatMap"
  override def severity = LintSeverity.Warning
}
