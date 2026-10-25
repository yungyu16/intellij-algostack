package icons

import javax.swing.Icon

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon


object AlgoStackIcons {
  @JvmField
  val PLUGIN: Icon = IconLoader.getIcon("/icons/pluginIcon.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val LOGIN: Icon =
    IconLoader.getIcon("/icons/login.svg", AlgoStackIcons::class.java.classLoader)
  @JvmField
  val LOGOUT: Icon =
    IconLoader.getIcon("/icons/logout.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val BUILDING: Icon =
    IconLoader.getIcon("/icons/building.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val EDIT_TESTCASES: Icon = IconLoader.getIcon("/icons/vialVirus.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val RUN: Icon = IconLoader.getIcon("/icons/run.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val SUBMIT: Icon = IconLoader.getIcon("/icons/submit.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val SOLUTION: Icon = IconLoader.getIcon("/icons/solution.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val CONSOLE: Icon = IconLoader.getIcon("/icons/console.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val SEARCH: Icon = IconLoader.getIcon("/icons/search.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val QUERY_PARAM: Icon = IconLoader.getIcon("/icons/filter.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val DESCRIPTION: Icon = IconLoader.getIcon("/icons/book.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val APP_BRACKETS: Icon = IconLoader.getIcon("/icons/app-brackets.svg", AlgoStackIcons::class.java.classLoader)

  @JvmField
  val LOADING: AnimatedIcon = AnimatedIcon.Default.INSTANCE

  object Dojos {
    @JvmField val LEETCODECN_PATH  = "/icons/dojos/leetcodecn.svg"
    @JvmField val LEETCODECN: Icon = IconLoader.getIcon(LEETCODECN_PATH, AlgoStackIcons::class.java.classLoader)
  }

  object Languages {
    @JvmField val ASM: Icon =
      IconLoader.getIcon("/icons/languages/asm.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val C: Icon =
      IconLoader.getIcon("/icons/languages/c.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val CLOJURE: Icon =
      IconLoader.getIcon("/icons/languages/clojure.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val CPP: Icon =
      IconLoader.getIcon("/icons/languages/cpp.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val CSHARP: Icon =
      IconLoader.getIcon("/icons/languages/csharp.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val CANGJIE: Icon =
      IconLoader.getIcon("/icons/languages/cangjie.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField val DLANG: Icon = IconLoader.getIcon("/icons/languages/dlang.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField val PASCAL: Icon =
      IconLoader.getIcon("/icons/languages/freepascal.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val DELPHI: Icon =
      IconLoader.getIcon("/icons/languages/delphi.svg", AlgoStackIcons::class.java.classLoader)

    @JvmField val DART: Icon =
      IconLoader.getIcon("/icons/languages/dart.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val ERLANG: Icon =
      IconLoader.getIcon("/icons/languages/erlang.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val ELIXIR: Icon =
      IconLoader.getIcon("/icons/languages/elixir.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val FSHARP: Icon =
      IconLoader.getIcon("/icons/languages/fsharp.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val GO: Icon =
      IconLoader.getIcon("/icons/languages/go.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val HASKELL: Icon =
      IconLoader.getIcon("/icons/languages/haskell.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val JAVA: Icon =
      IconLoader.getIcon("/icons/languages/java.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val JAVASCRIPT: Icon =
      IconLoader.getIcon("/icons/languages/javascript.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val JULIA: Icon =
      IconLoader.getIcon("/icons/languages/julia.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val KOTLIN: Icon =
      IconLoader.getIcon("/icons/languages/kotlin.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val OBJECTIVEC: Icon =
      IconLoader.getIcon("/icons/languages/objectivec.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val OCAML: Icon =
      IconLoader.getIcon("/icons/languages/ocaml.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val PERL: Icon =
      IconLoader.getIcon("/icons/languages/perl.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val PHP: Icon =
      IconLoader.getIcon("/icons/languages/php.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val PYTHON: Icon =
      IconLoader.getIcon("/icons/languages/python.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val R: Icon =
      IconLoader.getIcon("/icons/languages/r.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val RUBY: Icon =
      IconLoader.getIcon("/icons/languages/ruby.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val RUST: Icon =
      IconLoader.getIcon("/icons/languages/rust.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val RACKET: Icon =
      IconLoader.getIcon("/icons/languages/racket.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val SCALA: Icon =
      IconLoader.getIcon("/icons/languages/scala.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val SWIFT: Icon =
      IconLoader.getIcon("/icons/languages/swift.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val TYPESCRIPT: Icon =
      IconLoader.getIcon("/icons/languages/typescript.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val LUA: Icon =
      IconLoader.getIcon("/icons/languages/lua.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val LLVM: Icon =
      IconLoader.getIcon("/icons/languages/llvm.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val LISP: Icon =
      IconLoader.getIcon("/icons/languages/lisp.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val REASONML: Icon =
      IconLoader.getIcon("/icons/languages/reasonml.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val OCTAVE: Icon =
      IconLoader.getIcon("/icons/languages/octave.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val MYSQL: Icon =
      IconLoader.getIcon("/icons/languages/mysql.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val MSSQL: Icon =
      IconLoader.getIcon("/icons/languages/mssql.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val POSTGRESQL: Icon =
      IconLoader.getIcon("/icons/languages/postgresql.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val ORACLESQL: Icon =
      IconLoader.getIcon("/icons/languages/oraclesql.svg", AlgoStackIcons::class.java.classLoader)
    @JvmField val PANDAS: Icon =
      IconLoader.getIcon("/icons/languages/pandas.svg", AlgoStackIcons::class.java.classLoader)
  }
}
