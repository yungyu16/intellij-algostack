package icons

import javax.swing.Icon

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon


object CodeEpiphanyIcons {
  @JvmField
  val PLUGIN: Icon = IconLoader.getIcon("/icons/pluginIcon.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val LOGIN: Icon =
    IconLoader.getIcon("/icons/login.svg", CodeEpiphanyIcons::class.java.classLoader)
  @JvmField
  val LOGOUT: Icon =
    IconLoader.getIcon("/icons/logout.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val BUILDING: Icon =
    IconLoader.getIcon("/icons/building.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val EDIT_TESTCASES: Icon = IconLoader.getIcon("/icons/vialVirus.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val RUN: Icon = IconLoader.getIcon("/icons/run.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val SUBMIT: Icon = IconLoader.getIcon("/icons/submit.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val SOLUTION: Icon = IconLoader.getIcon("/icons/solution.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val CONSOLE: Icon = IconLoader.getIcon("/icons/console.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val SEARCH: Icon = IconLoader.getIcon("/icons/search.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val QUERY_PARAM: Icon = IconLoader.getIcon("/icons/filter.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val DESCRIPTION: Icon = IconLoader.getIcon("/icons/book.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val APP_BRACKETS: Icon = IconLoader.getIcon("/icons/app-brackets.svg", CodeEpiphanyIcons::class.java.classLoader)

  @JvmField
  val LOADING: AnimatedIcon = AnimatedIcon.Default.INSTANCE

  object Dojos {
    @JvmField val LEETCODECN_PATH  = "/icons/dojos/leetcodecn.svg"
    @JvmField val LEETCODE_PATH    = "/icons/dojos/leetcode.svg"
    @JvmField val LEETCODE: Icon   = IconLoader.getIcon(LEETCODE_PATH, CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val LEETCODECN: Icon = IconLoader.getIcon(LEETCODECN_PATH, CodeEpiphanyIcons::class.java.classLoader)
  }

  object Languages {
    @JvmField val ASM: Icon =
      IconLoader.getIcon("/icons/languages/asm.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val C: Icon =
      IconLoader.getIcon("/icons/languages/c.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val CLOJURE: Icon =
      IconLoader.getIcon("/icons/languages/clojure.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val CPP: Icon =
      IconLoader.getIcon("/icons/languages/cpp.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val CSHARP: Icon =
      IconLoader.getIcon("/icons/languages/csharp.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val CANGJIE: Icon =
      IconLoader.getIcon("/icons/languages/cangjie.svg", CodeEpiphanyIcons::class.java.classLoader)

    @JvmField val DLANG: Icon = IconLoader.getIcon("/icons/languages/dlang.svg", CodeEpiphanyIcons::class.java.classLoader)

    @JvmField val PASCAL: Icon =
      IconLoader.getIcon("/icons/languages/freepascal.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val DELPHI: Icon =
      IconLoader.getIcon("/icons/languages/delphi.svg", CodeEpiphanyIcons::class.java.classLoader)

    @JvmField val DART: Icon =
      IconLoader.getIcon("/icons/languages/dart.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val ERLANG: Icon =
      IconLoader.getIcon("/icons/languages/erlang.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val ELIXIR: Icon =
      IconLoader.getIcon("/icons/languages/elixir.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val FSHARP: Icon =
      IconLoader.getIcon("/icons/languages/fsharp.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val GO: Icon =
      IconLoader.getIcon("/icons/languages/go.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val HASKELL: Icon =
      IconLoader.getIcon("/icons/languages/haskell.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val JAVA: Icon =
      IconLoader.getIcon("/icons/languages/java.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val JAVASCRIPT: Icon =
      IconLoader.getIcon("/icons/languages/javascript.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val JULIA: Icon =
      IconLoader.getIcon("/icons/languages/julia.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val KOTLIN: Icon =
      IconLoader.getIcon("/icons/languages/kotlin.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val OBJECTIVEC: Icon =
      IconLoader.getIcon("/icons/languages/objectivec.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val OCAML: Icon =
      IconLoader.getIcon("/icons/languages/ocaml.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val PERL: Icon =
      IconLoader.getIcon("/icons/languages/perl.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val PHP: Icon =
      IconLoader.getIcon("/icons/languages/php.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val PYTHON: Icon =
      IconLoader.getIcon("/icons/languages/python.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val R: Icon =
      IconLoader.getIcon("/icons/languages/r.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val RUBY: Icon =
      IconLoader.getIcon("/icons/languages/ruby.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val RUST: Icon =
      IconLoader.getIcon("/icons/languages/rust.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val RACKET: Icon =
      IconLoader.getIcon("/icons/languages/racket.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val SCALA: Icon =
      IconLoader.getIcon("/icons/languages/scala.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val SWIFT: Icon =
      IconLoader.getIcon("/icons/languages/swift.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val TYPESCRIPT: Icon =
      IconLoader.getIcon("/icons/languages/typescript.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val LUA: Icon =
      IconLoader.getIcon("/icons/languages/lua.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val LLVM: Icon =
      IconLoader.getIcon("/icons/languages/llvm.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val LISP: Icon =
      IconLoader.getIcon("/icons/languages/lisp.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val REASONML: Icon =
      IconLoader.getIcon("/icons/languages/reasonml.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val OCTAVE: Icon =
      IconLoader.getIcon("/icons/languages/octave.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val MYSQL: Icon =
      IconLoader.getIcon("/icons/languages/mysql.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val MSSQL: Icon =
      IconLoader.getIcon("/icons/languages/mssql.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val POSTGRESQL: Icon =
      IconLoader.getIcon("/icons/languages/postgresql.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val ORACLESQL: Icon =
      IconLoader.getIcon("/icons/languages/oraclesql.svg", CodeEpiphanyIcons::class.java.classLoader)
    @JvmField val PANDAS: Icon =
      IconLoader.getIcon("/icons/languages/pandas.svg", CodeEpiphanyIcons::class.java.classLoader)
  }
}
