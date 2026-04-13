#!/usr/bin/env python3
from dataclasses import dataclass
import dataclasses
from pathlib import Path
import re


@dataclass
class Config:
    type: str  # the target type name, e.g. "Int", "Double"
    lower_type: str  # the lower-case version of the type, e.g. "int", "double"
    zero: str  # the default "zero" for non-numeric purposes (used in field initialization, etc.)
    sum_zero: str  # the zero value for summing (which sometimes must be different)
    numeric: bool  # whether to include numeric operations (like sum/average)
    comparable: bool  # whether to include operations that require natural ordering (min, max, sort, etc.)
    same_value_expr: str  # expression used to compare two values, using names left/right
    hash_value_expr: str  # expression used to hash one value, using name value


# Define the configuration for each primitive type.
# (You may adjust the flags as needed. For example, Boolean is neither numeric nor comparable.)
primitive_types = [
    Config(
        "Boolean", "boolean", "false", "false", numeric=False, comparable=False,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),
    Config(
        "Byte", "byte", "0", "0", numeric=True, comparable=True,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),
    Config(
        "Short", "short", "0", "0", numeric=True, comparable=True,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),
    Config(
        "Char", "char", "0.toChar()", "0.toChar()", numeric=False, comparable=True,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),  # arithmetic operations usually don’t make sense for Char
    Config(
        "Float", "float", "0f", "0f", numeric=True, comparable=True,
        same_value_expr="left.equals(right)",
        hash_value_expr="value.hashCode()",
    ),
    Config(
        "Int", "int", "0", "0", numeric=True, comparable=True,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),
    Config(
        "Long", "long", "0L", "0L", numeric=True, comparable=True,
        same_value_expr="left == right", hash_value_expr="value.hashCode()",
    ),
    Config(
        "Double", "double", "0.0", "0.0", numeric=True, comparable=True,
        same_value_expr="left.equals(right)",
        hash_value_expr="value.hashCode()",
    ),
]


def process_conditionals(content: str, condition: bool, marker: str) -> str:
    if condition:
        return content

    lines = content.splitlines(keepends=True)
    result = []
    i = 0

    while i < len(lines):
        has_section_header = (
            i + 2 < len(lines)
            and lines[i].lstrip().startswith("//")
            and "////" in lines[i]
            and lines[i + 1].lstrip().startswith("//")
            and marker in lines[i + 1]
            and lines[i + 2].lstrip().startswith("//")
            and "////" in lines[i + 2]
        )
        if not has_section_header:
            result.append(lines[i])
            i += 1
            continue

        i += 3
        while i < len(lines):
            next_section_header = (
                i + 2 < len(lines)
                and lines[i].lstrip().startswith("//")
                and "////" in lines[i]
                and lines[i + 1].lstrip().startswith("//")
                and lines[i + 2].lstrip().startswith("//")
                and "////" in lines[i + 2]
            )
            if next_section_header:
                break
            i += 1

    return "".join(result)


s = """
    ///////////////////////////////////////////////////////////////////////////
    // Iterable C + Numeric T
    ///////////////////////////////////////////////////////////////////////////

    fun sum(): Char {
        var s = 0.toChar()
        for (i in 0 until usedSize) s += buffer[i]
        return s
    }

    ///////////////////////////////////////////////////////////////////////////
    // Test
    ///////////////////////////////////////////////////////////////////////////
"""
r = """
    ///////////////////////////////////////////////////////////////////////////
    // Test
    ///////////////////////////////////////////////////////////////////////////
"""

assert process_conditionals(s, False, "Numeric T") == r, process_conditionals(
    s, False, "Numeric T"
)


def process_content(content: str, config: "Config") -> str:
    """
    Process the source content by conditionally including/excluding
    sections that require traits such as "Numeric T" or "Comparable T".
    """
    content = process_conditionals(content, config.numeric, "Numeric T")
    content = process_conditionals(content, config.comparable, "Comparable T")
    return content


def render_template(content: str, config: Config) -> str:
    rendered = (
        content.replace("Float", "{{ type }}")
        .replace("float", "{{ lower_type }}")
        .replace("0f", "{{ zero }}")
    )
    rendered = re.sub(
        r"private fun sameValue\(left: \{\{ type \}\}, right: \{\{ type \}\}\): Boolean = .+",
        "private fun sameValue(left: {{ type }}, right: {{ type }}): Boolean = {{ same_value_expr }}",
        rendered,
    )
    rendered = re.sub(
        r"private fun hashValue\(value: \{\{ type \}\}\): Int = .+",
        "private fun hashValue(value: {{ type }}): Int = {{ hash_value_expr }}",
        rendered,
    )
    for key, value in dataclasses.asdict(config).items():
        rendered = rendered.replace(f"{{{{ {key} }}}}", str(value))
    return rendered


if __name__ == "__main__":
    project_root = Path(__file__).parent
    main_package = (
        project_root / "src" / "commonMain" / "kotlin" / "one" / "wabbit" / "data"
    )

    # buffer_template = Template(open(main_package / 'BaseBuffer.kt.tmpl', 'rt').read())
    # deque_template = Template(open(main_package / 'BaseDeque.kt.tmpl', 'rt').read())

    base_buffer_text = (main_package / "FloatBuffer.kt").read_text()
    base_deque_text = (main_package / "FloatDeque.kt").read_text()

    for tpe in primitive_types:
        buffer_text = process_content(base_buffer_text, tpe)
        deque_text = process_content(base_deque_text, tpe)

        (main_package / f"{tpe.type}Buffer.kt").write_text(render_template(buffer_text, tpe))
        (main_package / f"{tpe.type}Deque.kt").write_text(render_template(deque_text, tpe))
