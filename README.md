# worksheet-to-db #

## Summary ##

worksheet-to-db is small program to import content of
Excel file into SQL database as DB table (or tables).


## Background ##

This project was started, when I found, that I need to work
with big Excel files to process time-booking data. It is
possible to work with this data in Excel, but selecting
manually several thousands of rows is something which I
did not like. I am also not a fan of Excel Plugin/Macros
API, so I decided, that it would be easier for me to
work with data using SQL.

I also tried to use GUI of LibreOffice to copy-paste
data from Calc application (worksheets) to Base application
(database), but for some reason it did not work.

So, I decided, that I can write importer myself. And so
this project was born.

So far I used it with H2 embedded database (i.e. one, stored
directly in single file). But it should work with more
or less any modern DB out of the box (probably drivers
for corresponding DB need to be added as a dependency
to `build.gradle`).


## TODO ##

* Change code to (optionally) create "id" column.
* Use "event/SAX" api to read big excel files.


## Various stuff #

This project uses assertions, so during development it is recommended to run
the applucation with `-enableassertions` java flag (see also 
[https://docs.oracle.com/javase/7/docs/technotes/guides/language/assert.html#enable-disable](https://docs.oracle.com/javase/7/docs/technotes/guides/language/assert.html#enable-disable))


## Copyright and license ##

Copyright (C) 2021 Dmitrii Semikin <https://dmitrii.semikin.xyz>

This project is published under the AGPL v3 license.
See [LICENSE.txt](LICENSE.txt) for details.
