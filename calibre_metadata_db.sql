-- select string_agg(sql, '|||SEP|||') from sqlite_master
-- where type = 'table'

CREATE TABLE authors ( id   INTEGER PRIMARY KEY,
                              name TEXT NOT NULL COLLATE NOCASE,
                              sort TEXT COLLATE NOCASE,
                              link TEXT NOT NULL DEFAULT "",
                              UNIQUE(name)
                             )

CREATE TABLE books ( id      INTEGER PRIMARY KEY AUTOINCREMENT,
                             title     TEXT NOT NULL DEFAULT 'Unknown' COLLATE NOCASE,
                             sort      TEXT COLLATE NOCASE,
                             timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             pubdate   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             series_index REAL NOT NULL DEFAULT 1.0,
                             author_sort TEXT COLLATE NOCASE,
                             isbn TEXT DEFAULT "" COLLATE NOCASE,
                             lccn TEXT DEFAULT "" COLLATE NOCASE,
                             path TEXT NOT NULL DEFAULT "",
                             flags INTEGER NOT NULL DEFAULT 1,
                             uuid TEXT,
                             has_cover BOOL DEFAULT 0,
                             last_modified TIMESTAMP NOT NULL DEFAULT "2000-01-01 00:00:00+00:00")

CREATE TABLE sqlite_sequence(name,seq)

CREATE TABLE books_authors_link ( id INTEGER PRIMARY KEY,
                                          book INTEGER NOT NULL,
                                          author INTEGER NOT NULL,
                                          UNIQUE(book, author)
                                        )

CREATE TABLE books_languages_link ( id INTEGER PRIMARY KEY,
                                            book INTEGER NOT NULL,
                                            lang_code INTEGER NOT NULL,
                                            item_order INTEGER NOT NULL DEFAULT 0,
                                            UNIQUE(book, lang_code)
        )

CREATE TABLE books_plugin_data(id INTEGER PRIMARY KEY,
                                     book INTEGER NOT NULL,
                                     name TEXT NOT NULL,
                                     val TEXT NOT NULL,
                                     UNIQUE(book,name))

CREATE TABLE books_publishers_link ( id INTEGER PRIMARY KEY,
                                          book INTEGER NOT NULL,
                                          publisher INTEGER NOT NULL,
                                          UNIQUE(book)
                                        )

CREATE TABLE books_ratings_link ( id INTEGER PRIMARY KEY,
                                          book INTEGER NOT NULL,
                                          rating INTEGER NOT NULL,
                                          UNIQUE(book, rating)
                                        )

CREATE TABLE books_series_link ( id INTEGER PRIMARY KEY,
                                          book INTEGER NOT NULL,
                                          series INTEGER NOT NULL,
                                          UNIQUE(book)
                                        )

CREATE TABLE books_tags_link ( id INTEGER PRIMARY KEY,
                                          book INTEGER NOT NULL,
                                          tag INTEGER NOT NULL,
                                          UNIQUE(book, tag)
                                        )

CREATE TABLE comments ( id INTEGER PRIMARY KEY,
                              book INTEGER NOT NULL,
                              text TEXT NOT NULL COLLATE NOCASE,
                              UNIQUE(book)
                            )

CREATE TABLE conversion_options ( id INTEGER PRIMARY KEY,
                                          format TEXT NOT NULL COLLATE NOCASE,
                                          book INTEGER,
                                          data BLOB NOT NULL,
                                          UNIQUE(format,book)
                                        )

CREATE TABLE custom_columns (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    label    TEXT NOT NULL,
                    name     TEXT NOT NULL,
                    datatype TEXT NOT NULL,
                    mark_for_delete   BOOL DEFAULT 0 NOT NULL,
                    editable BOOL DEFAULT 1 NOT NULL,
                    display  TEXT DEFAULT "{}" NOT NULL,
                    is_multiple BOOL DEFAULT 0 NOT NULL,
                    normalized BOOL NOT NULL,
                    UNIQUE(label)
                )

CREATE TABLE data ( id     INTEGER PRIMARY KEY,
                            book   INTEGER NOT NULL,
                            format TEXT NOT NULL COLLATE NOCASE,
                            uncompressed_size INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            UNIQUE(book, format)
)

CREATE TABLE feeds ( id   INTEGER PRIMARY KEY,
                              title TEXT NOT NULL,
                              script TEXT NOT NULL,
                              UNIQUE(title)
                             )

CREATE TABLE identifiers  ( id     INTEGER PRIMARY KEY,
                                    book   INTEGER NOT NULL,
                                    type   TEXT NOT NULL DEFAULT "isbn" COLLATE NOCASE,
                                    val    TEXT NOT NULL COLLATE NOCASE,
                                    UNIQUE(book, type)
        )

CREATE TABLE languages    ( id        INTEGER PRIMARY KEY,
                                    lang_code TEXT NOT NULL COLLATE NOCASE, link TEXT NOT NULL DEFAULT '',
                                    UNIQUE(lang_code)
        )

CREATE TABLE library_id ( id   INTEGER PRIMARY KEY,
                                  uuid TEXT NOT NULL,
                                  UNIQUE(uuid)
        )

CREATE TABLE metadata_dirtied(id INTEGER PRIMARY KEY,
                             book INTEGER NOT NULL,
                             UNIQUE(book))

CREATE TABLE preferences(id INTEGER PRIMARY KEY,
                                 key TEXT NOT NULL,
                                 val TEXT NOT NULL,
                                 UNIQUE(key))

CREATE TABLE publishers ( id   INTEGER PRIMARY KEY,
                                  name TEXT NOT NULL COLLATE NOCASE,
                                  sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '',
                                  UNIQUE(name)
                             )

CREATE TABLE ratings ( id   INTEGER PRIMARY KEY,
                               rating INTEGER CHECK(rating > -1 AND rating < 11), link TEXT NOT NULL DEFAULT '',
                               UNIQUE (rating)
                             )

CREATE TABLE series ( id   INTEGER PRIMARY KEY,
                              name TEXT NOT NULL COLLATE NOCASE,
                              sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '',
                              UNIQUE (name)
                             )

CREATE TABLE tags ( id   INTEGER PRIMARY KEY,
                            name TEXT NOT NULL COLLATE NOCASE, link TEXT NOT NULL DEFAULT '',
                            UNIQUE (name)
                             )

CREATE TABLE last_read_positions ( id INTEGER PRIMARY KEY,
	book INTEGER NOT NULL,
	format TEXT NOT NULL COLLATE NOCASE,
	user TEXT NOT NULL,
	device TEXT NOT NULL,
	cfi TEXT NOT NULL,
	epoch REAL NOT NULL,
	pos_frac REAL NOT NULL DEFAULT 0,
	UNIQUE(user, device, book, format)
)

CREATE TABLE annotations_dirtied(id INTEGER PRIMARY KEY,
                             book INTEGER NOT NULL,
                             UNIQUE(book))

CREATE TABLE annotations ( id INTEGER PRIMARY KEY,
    book INTEGER NOT NULL,
    format TEXT NOT NULL COLLATE NOCASE,
    user_type TEXT NOT NULL,
    user TEXT NOT NULL,
    timestamp REAL NOT NULL,
    annot_id TEXT NOT NULL,
    annot_type TEXT NOT NULL,
    annot_data TEXT NOT NULL,
    searchable_text TEXT NOT NULL DEFAULT "",
    UNIQUE(book, user_type, user, format, annot_type, annot_id)
)

CREATE VIRTUAL TABLE annotations_fts USING fts5(searchable_text,
    content = 'annotations', content_rowid = 'id', tokenize = 'unicode61 remove_diacritics 2')

CREATE TABLE 'annotations_fts_data'(id INTEGER PRIMARY KEY, block BLOB)

CREATE TABLE 'annotations_fts_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID

CREATE TABLE 'annotations_fts_docsize'(id INTEGER PRIMARY KEY, sz BLOB)

CREATE TABLE 'annotations_fts_config'(k PRIMARY KEY, v) WITHOUT ROWID

CREATE VIRTUAL TABLE annotations_fts_stemmed USING fts5(searchable_text,
    content = 'annotations', content_rowid = 'id', tokenize = 'porter unicode61 remove_diacritics 2')

CREATE TABLE 'annotations_fts_stemmed_data'(id INTEGER PRIMARY KEY, block BLOB)

CREATE TABLE 'annotations_fts_stemmed_idx'(segid, term, pgno, PRIMARY KEY(segid, term)) WITHOUT ROWID

CREATE TABLE 'annotations_fts_stemmed_docsize'(id INTEGER PRIMARY KEY, sz BLOB)

CREATE TABLE 'annotations_fts_stemmed_config'(k PRIMARY KEY, v) WITHOUT ROWID