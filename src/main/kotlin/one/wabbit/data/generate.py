from jinja2 import Template
import os

path = os.path.dirname(os.path.abspath(__file__))

template = Template(open(os.path.join(path, 'BaseBuf.tmpl'), 'rt').read())

types = ['Byte', 'Int', 'Long', 'Double', 'Boolean', 'Float', 'Short', 'Char']

for tpe in types:
    result = template.render(type=tpe)
    with open(os.path.join(path, f'{tpe}Buf.kt'), 'wt+') as fout:
        fout.write(result)
