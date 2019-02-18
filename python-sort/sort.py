import sys
import bibtexparser
import textwrap

if len(sys.argv) != 2:
  print("The path to the bibtex file was expected as argument")
  exit(1)

with open(sys.argv[1], 'r') as bibfile:
    bp = bibtexparser.load(bibfile)
    entries = bp.entries

# field, format, wrap or not
field_order = [(u'author', '{{{0}}},\n', False),
               (u'title', '{{{0}}},\n', False),
               (u'type', '{{{0}}},\n', False),
               (u'journal','{{{0}}},\n', False),
               (u'booktitle','{{{0}}},\n', False),
               (u'series','{{{0}}},\n', False),
               (u'volume','{{{0}}},\n', False),
               (u'edition','{{{0}}},\n', False),
               (u'number', '{{{0}}},\n', False),
               (u'pages', '{{{0}}},\n', False),
               (u'numpages', '{{{0}}},\n', False),
               (u'year', '{{{0}}},\n', False),
               (u'doi','{{{0}}},\n', False),
               (u'isbn','{{{0}}},\n', False),
               (u'issn','{{{0}}},\n', False),
               (u'publisher','{{{0}}},\n', False),
               (u'editor','{{{0}}},\n', False),
               (u'institution','{{{0}}},\n', False),
               (u'url','{{{0}}},\n', False),
               (u'urldate','{{{0}}},\n', False),
               (u'link','{{{0}}},\n', False),
               (u'eprint','{{{0}}},\n', False),
               (u'keywords','{{{0}}},\n', False),
               (u'note','{{{0}}},\n', False),
               (u'abstract','{{{0}}},\n', False),
               (u'file','{{{0}}},\n', False)]

# pick an entry, this time second to last one
for entry in entries:
  keys = set(entry.keys())

  extra_fields = keys.difference([f[0] for f in field_order])

  # we do not want these in our entry, they go in the "header"
  extra_fields.remove('ENTRYTYPE')
  extra_fields.remove('ID')

  # Now build up our entry string
  s = '@{type}{{{id},\n'.format(type=entry['ENTRYTYPE'].title(),
                                id=entry['ID'])

  # Now handle the ordered fields, then the extra fields
  for field, fmt, wrap in field_order:
      if field in entry:
          s1 = '  {0} '.format(field.lower())
          s2 = fmt.format(entry[field])
          s3 = '{0:11s}= {1}'.format(s1, s2)
          if wrap:
              # fill seems to remove trailing '\n'
              s3 = textwrap.fill(s3, subsequent_indent=' '*18, width=70) + '\n'
          s += s3

  for field in extra_fields:
      if field in entry:
          s1 = '  {0} '.format(field.lower())
          s2 = entry[field]
          s3 = '{0:11s}= {{{1}}},'.format(s1, s2) + '\n'
          # s3 = textwrap.fill(s3, subsequent_indent=' '*18, width=70) + '\n'
          s += s3

  s += '}\n'
  print(s)
