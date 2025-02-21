#!/usr/bin/env python3
from dataclasses import dataclass, asdict
import dataclasses
from jinja2 import Template
from pathlib import Path
import re

@dataclass
class Config:
    type: str         # the target type name, e.g. "Int", "Double"
    lower_type: str   # the lower-case version of the type, e.g. "int", "double"
    zero: str         # the default "zero" for non-numeric purposes (used in field initialization, etc.)
    sum_zero: str     # the zero value for summing (which sometimes must be different)
    numeric: bool     # whether to include numeric operations (like sum/average)
    comparable: bool  # whether to include operations that require natural ordering (min, max, sort, etc.)

# Define the configuration for each primitive type.
# (You may adjust the flags as needed. For example, Boolean is neither numeric nor comparable.)
primitive_types = [
    Config('Boolean', 'boolean', 'false',      'false',      numeric=False, comparable=False),
    Config('Byte',    'byte',    '0',          '0',          numeric=True,  comparable=True),
    Config('Short',   'short',   '0',          '0',          numeric=True,  comparable=True),
    Config('Char',    'char',    '0.toChar()', '0.toChar()', numeric=False, comparable=True),  # arithmetic operations usually don’t make sense for Char
    Config('Int',     'int',     '0',          '0',          numeric=True,  comparable=True),
    Config('Long',    'long',    '0L',         '0L',         numeric=True,  comparable=True),
    Config('Double',  'double',  '0.0',        '0.0',        numeric=True,  comparable=True)
]

def process_conditionals(content: str, condition: bool, marker: str) -> str:
    # This regex assumes that the marker is written exactly (for example "Comparable T")
    pattern = re.compile(
        r'    //[^/]*?' + re.escape(marker) + r'[^/]*?' +
        r'    ///////////////////////////////////////////////////////////////////////////' +
        r'[^/]*?' +
        r'    ///////////////////////////////////////////////////////////////////////////\n',
        re.DOTALL | re.MULTILINE
    )
    if condition:
        return content
    else:
        # Remove the whole block.
        return pattern.sub('', content)

s = '''
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
'''
r = '''
    ///////////////////////////////////////////////////////////////////////////
    // Test
    ///////////////////////////////////////////////////////////////////////////
'''

assert process_conditionals(s, False, "Numeric T") == r, process_conditionals(s, False, "Numeric T")

def process_content(content: str, config: 'Config') -> str:
    """
    Process the source content by conditionally including/excluding
    sections that require traits such as "Numeric T" or "Comparable T".
    """
    content = process_conditionals(content, config.numeric, "Numeric T")
    content = process_conditionals(content, config.comparable, "Comparable T")
    return content

if __name__ == '__main__':
    project_root = Path(__file__).parent
    main_package = project_root / 'src' / 'main' / 'kotlin' / 'one' / 'wabbit' / 'data'

    # buffer_template = Template(open(main_package / 'BaseBuffer.kt.tmpl', 'rt').read())
    # deque_template = Template(open(main_package / 'BaseDeque.kt.tmpl', 'rt').read())

    base_buffer_text = open(main_package / 'FloatBuffer.kt', 'rt').read()
    base_deque_text = open(main_package / 'FloatDeque.kt', 'rt').read()

    for tpe in primitive_types:
        buffer_text = process_content(base_buffer_text, tpe)
        deque_text = process_content(base_deque_text, tpe)

        buffer_template = Template(
            buffer_text
                .replace('Float', '{{ type }}')
                .replace('float', '{{ lower_type }}')
                .replace('0f', '{{ zero }}')
        )
        deque_template = Template(
            deque_text
                .replace('Float', '{{ type }}')
                .replace('float', '{{ lower_type }}')
                .replace('0f', '{{ zero }}')
        )

        with open(main_package / f'{tpe.type}Buffer.kt', 'wt+') as fout:
            fout.write(buffer_template.render(**dataclasses.asdict(tpe)))
        with open(main_package / f'{tpe.type}Deque.kt', 'wt+') as fout:
            fout.write(deque_template.render(**dataclasses.asdict(tpe)))