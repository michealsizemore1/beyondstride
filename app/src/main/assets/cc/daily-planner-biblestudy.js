/* 📖 Daily Bible Study (Nov 2026)
 * Built around the One Year Chronological Bible (Tyndale, NLT): each day's study follows that day's reading.
 * One guided session through the four daily Bible areas:
 *   1 🙏 Opening prayer (ticks AI Prayer) → 2 📖 Read today's passages, 🎧 Listen / "I listened in my app" (ticks Bible Audio)
 *   → 3 🔍 Study, SOAP (context, key word, cross-references, your answers) → 4 🎶 Worship (ticks Worship)
 *   → 5 🙏 Closing prayer from your application (ticks Daily Bread on finish).
 * Plus: pick another day (if you're behind/ahead), fix a day's reading, other plans (a book, or the Daily Bread verse),
 * a searchable study journal, a weekly memory verse, and the week's studies fed to the Weekly AI Review.
 * On-screen text is the World English Bible (public domain, bible-api.com); "Read in NLT" opens Bible Gateway.
 * Reading list: the One Year Chronological Bible schedule (a few obvious typos in the published list corrected). */
(function(){
  var BOOKS=[['Genesis',50],['Exodus',40],['Leviticus',27],['Numbers',36],['Deuteronomy',34],['Joshua',24],['Judges',21],['Ruth',4],['1 Samuel',31],['2 Samuel',24],['1 Kings',22],['2 Kings',25],['1 Chronicles',29],['2 Chronicles',36],['Ezra',10],['Nehemiah',13],['Esther',10],['Job',42],['Psalms',150],['Proverbs',31],['Ecclesiastes',12],['Song of Solomon',8],['Isaiah',66],['Jeremiah',52],['Lamentations',5],['Ezekiel',48],['Daniel',12],['Hosea',14],['Joel',3],['Amos',9],['Obadiah',1],['Jonah',4],['Micah',7],['Nahum',3],['Habakkuk',3],['Zephaniah',3],['Haggai',2],['Zechariah',14],['Malachi',4],
    ['Matthew',28],['Mark',16],['Luke',24],['John',21],['Acts',28],['Romans',16],['1 Corinthians',16],['2 Corinthians',13],['Galatians',6],['Ephesians',6],['Philippians',4],['Colossians',4],['1 Thessalonians',5],['2 Thessalonians',3],['1 Timothy',6],['2 Timothy',4],['Titus',3],['Philemon',1],['Hebrews',13],['James',5],['1 Peter',5],['2 Peter',3],['1 John',5],['2 John',1],['3 John',1],['Jude',1],['Revelation',22]];
  var OYB_RAW='01-01:GENESIS 1:1-3:24|01-02:GENESIS 4:1-5:32;1 CHRONICLES 1:1-4;GENESIS 6:1-22|01-03:GENESIS 7:1-10:5;1 CHRONICLES 1:5-7;GENESIS 10:6-20;1 CHRONICLES 1:8-16;GENESIS 10:21-30;1 CHRONICLES 1:17-23;GENESIS 10:31-32|01-04:GENESIS 11:1-26;1 CHRONICLES 1:24-27;GENESIS 11:27-14:24|01-05:GENESIS 15:1-17:27|01-06:GENESIS 18:1-21:7|01-07:GENESIS 21:8-23:20;GENESIS 11:32;GENESIS 24:1-67|01-08:GENESIS 25:1-4;1 CHRONICLES 1:32-33;GENESIS 25:5-6;GENESIS 25:12-18;1 CHRONICLES 1:28-31;1 CHRONICLES 1:34;GENESIS 25:19-26;GENESIS 25:7-11|01-09:GENESIS 25:27-28:5|01-10:GENESIS 28:6-30:24|01-11:GENESIS 30:25-31:55|01-12:GENESIS 32:1-35:27|01-13:GENESIS 36:1-19;1 CHRONICLES 1:35-37;GENESIS 36:20-30;1 CHRONICLES 1:38-42;GENESIS 36:31-43;1 CHRONICLES 1:43-2:2|01-14:GENESIS 37:1-38:30;1 CHRONICLES 2:3-6;1 CHRONICLES 2:8;GENESIS 39:1-23|01-15:GENESIS 40:1-23;GENESIS 35:28-29;GENESIS 41:1-57|01-16:GENESIS 42:1-45:15|01-17:GENESIS 45:16-47:27|01-18:GENESIS 47:28-50:26|01-19:JOB 1:1-4:21|01-20:JOB 5:1-7:21|01-21:JOB 8:1-11:20|01-22:JOB 12:1-14:22|01-23:JOB 15:1-18:21|01-24:JOB 19:1-21:34|01-25:JOB 22:1-25:6|01-26:JOB 26:1-29:25|01-27:JOB 30:1-31:40|01-28:JOB 32:1-34:37|01-29:JOB 35:1-37:24|01-30:JOB 38:1-40:5|01-31:JOB 40:6-42:17|02-01:EXODUS 1:1-2:25;1 CHRONICLES 6:1-3;EXODUS 3:1-4:17|02-02:EXODUS 4:18-7:13|02-03:EXODUS 7:14-9:35|02-04:EXODUS 10:1-12:51|02-05:EXODUS 13:1-15:27|02-06:EXODUS 16:1-19:25|02-07:EXODUS 20:1-22:15|02-08:EXODUS 22:16-24:18|02-09:EXODUS 25:1-28:43|02-10:EXODUS 29:1-31:18|02-11:EXODUS 32:1-34:35|02-12:EXODUS 35:1-36:38|02-13:EXODUS 37:1-39:31|02-14:EXODUS 39:32-40:38;NUMBERS 9:15-23|02-15:NUMBERS 7:1-89|02-16:NUMBERS 8:1-9:14;LEVITICUS 1:1-3:17|02-17:LEVITICUS 4:1-6:30|02-18:LEVITICUS 7:1-8:36|02-19:LEVITICUS 9:1-11:47|02-20:LEVITICUS 12:1-14:32|02-21:LEVITICUS 14:33-16:34|02-22:LEVITICUS 17:1-19:37|02-23:LEVITICUS 20:1-22:33|02-24:LEVITICUS 23:1-25:23|02-25:LEVITICUS 25:24-26:46|02-26:LEVITICUS 27:1-34;NUMBERS 1:1-54|02-27:NUMBERS 2:1-3:51|02-28:NUMBERS 4:1-5:31|03-01:NUMBERS 6:1-27;NUMBERS 10:1-36|03-02:NUMBERS 11:1-13:33|03-03:NUMBERS 14:1-15:41|03-04:NUMBERS 16:1-18:32|03-05:NUMBERS 19:1-21:35|03-06:NUMBERS 22:1-24:25|03-07:NUMBERS 25:1-26:65|03-08:NUMBERS 27:1-29:40|03-09:NUMBERS 30:1-31:54|03-10:NUMBERS 32:1-33:56|03-11:NUMBERS 34:1-36:13|03-12:DEUTERONOMY 1:1-3:20|03-13:DEUTERONOMY 3:21-5:33|03-14:DEUTERONOMY 6:1-9:29|03-15:DEUTERONOMY 10:1-12:32|03-16:DEUTERONOMY 13:1-16:17|03-17:DEUTERONOMY 16:18-21:9|03-18:DEUTERONOMY 21:10-25:19|03-19:DEUTERONOMY 26:1-29:1|03-20:DEUTERONOMY 29:2-31:29|03-21:DEUTERONOMY 31:30-32:52;PSALMS 90|03-22:DEUTERONOMY 33:1-34:12;JOSHUA 1:1-2:24|03-23:JOSHUA 3:1-6:27|03-24:JOSHUA 7:1;1 CHRONICLES 2:7;JOSHUA 7:2-9:27|03-25:JOSHUA 10:1-12:6|03-26:JOSHUA 12:7-15:19|03-27:JOSHUA 15:20-17:18|03-28:JOSHUA 18:1-19:48|03-29:JOSHUA 19:49-21:45;1 CHRONICLES 6:54-81|03-30:JOSHUA 22:1-24:33|03-31:JUDGES 1:1-3:30|04-01:JUDGES 3:31-6:40|04-02:JUDGES 7:1-9:21|04-03:JUDGES 9:22-11:28|04-04:JUDGES 11:29-15:20|04-05:JUDGES 16:1-18:31|04-06:JUDGES 19:1-21:25|04-07:RUTH 1:1-4:12|04-08:RUTH 4:13-22;1 CHRONICLES 2:9-55;1 CHRONICLES 4:1-23;1 SAMUEL 1:1-8|04-09:1 SAMUEL 1:9-4:11|04-10:1 SAMUEL 4:12-8:22|04-11:1 SAMUEL 9:1-12:25|04-12:1 CHRONICLES 9:35-39;1 SAMUEL 13:1-5;1 SAMUEL 13:19-23;1 SAMUEL 13:6-18;1 SAMUEL 14:1-52|04-13:1 SAMUEL 15:1-17:31|04-14:1 SAMUEL 17:32-19:17;PSALMS 59;1 SAMUEL 19:18-24|04-15:1 SAMUEL 20:1-21:15;PSALMS 34|04-16:1 SAMUEL 22:1-2;PSALMS 57;PSALMS 142;1 CHRONICLES 12:8-18;1 SAMUEL 22:3-23;PSALMS 52;1 SAMUEL 23:1-12|04-17:1 SAMUEL 23:13-29;PSALMS 54;1 SAMUEL 24:1-25:44|04-18:1 SAMUEL 26:1-27:7;1 CHRONICLES 12:1-7;1 SAMUEL 27:8-29:11;1 CHRONICLES 12:19;PSALMS 56|04-19:1 SAMUEL 30:1-31;1 CHRONICLES 12:20-22;1 SAMUEL 31:1-13;1 CHRONICLES 10:1-14;1 CHRONICLES 9:40-44;2 SAMUEL 4:4;2 SAMUEL 1:1-27|04-20:2 SAMUEL 2:1-3:5;1 CHRONICLES 3:1-4;2 SAMUEL 23:8-17;1 CHRONICLES 11:10-19;2 SAMUEL 23:18-39;1 CHRONICLES 11:20-47|04-21:2 SAMUEL 3:6-4:12|04-22:2 SAMUEL 5:1-3;1 CHRONICLES 11:1-3;1 CHRONICLES 12:23-40;2 SAMUEL 5:17-25;1 CHRONICLES 14:8-17;2 SAMUEL 5:6-10;1 CHRONICLES 11:4-9;1 CHRONICLES 3:4;2 SAMUEL 5:13;2 SAMUEL 5:4-5;2 SAMUEL 5:11-12;1 CHRONICLES 14:1-2;1 CHRONICLES 13:1-5;2 SAMUEL 6:1-11;1 CHRONICLES 13:6-14|04-23:2 SAMUEL 6:12;1 CHRONICLES 15:1-28;2 SAMUEL 6:12-16;1 CHRONICLES 15:29;2 SAMUEL 6:17-19;1 CHRONICLES 16:1-43;2 SAMUEL 6:19-23|04-24:2 SAMUEL 7:1-17;1 CHRONICLES 17:1-15;2 SAMUEL 7:18-29;1 CHRONICLES 17:16-27;2 SAMUEL 8:1-14;1 CHRONICLES 18:1-13;PSALMS 60|04-25:2 SAMUEL 8:15-18;1 CHRONICLES 18:14-17;1 CHRONICLES 6:16-30;1 CHRONICLES 6:50-53;1 CHRONICLES 6:31-48;2 SAMUEL 9:1-10:19;1 CHRONICLES 19:1-19|04-26:1 CHRONICLES 20:1;2 SAMUEL 11:1-12:14;PSALMS 51;2 SAMUEL 12:15-25;2 SAMUEL 5:14-16;1 CHRONICLES 14:3-7;1 CHRONICLES 3:5-9|04-27:2 SAMUEL 12:26-31;1 CHRONICLES 20:2-3;2 SAMUEL 13:1-14:33|04-28:2 SAMUEL 15:1-17:14|04-29:2 SAMUEL 17:15-29;PSALMS 3;PSALMS 63;2 SAMUEL 18:1-19:30|04-30:2 SAMUEL 19:31-20:26;PSALMS 7;2 SAMUEL 21:1-22;1 CHRONICLES 20:4-8|05-01:2 SAMUEL 22:1-51;PSALMS 18|05-02:2 SAMUEL 24:1-9;1 CHRONICLES 21:1-6;2 SAMUEL 24:10-17;1 CHRONICLES 21:7-17;2 SAMUEL 24:18-25;1 CHRONICLES 21:18-22:19|05-03:1 CHRONICLES 23:1-25:31|05-04:1 CHRONICLES 26:1-28:21|05-05:1 CHRONICLES 29:1-22;1 KINGS 1:1-53|05-06:1 KINGS 2:1-9;2 SAMUEL 23:1-7;1 KINGS 2:10-12;1 CHRONICLES 29:26-30;PSALMS 4-6;PSALMS 8-9;PSALMS 11|05-07:PSALMS 12-17;PSALMS 19-21|05-08:PSALMS 22-26|05-09:PSALMS 27-32|05-10:PSALMS 35-38|05-11:PSALMS 39-41;PSALMS 53;PSALMS 55;PSALMS 58|05-12:PSALMS 61-62;PSALMS 64-67|05-13:PSALMS 68-70;PSALMS 86;PSALMS 101|05-14:PSALMS 103;PSALMS 108-110;PSALMS 122;PSALMS 124|05-15:PSALMS 131;PSALMS 133;PSALMS 138-141;PSALMS 143|05-16:PSALMS 144-145;PSALMS 88-89|05-17:PSALMS 50;PSALMS 73-74|05-18:PSALMS 75-78|05-19:PSALMS 79-82|05-20:PSALMS 83;1 CHRONICLES 29:23-25;2 CHRONICLES 1:1;1 KINGS 2:13-3:4;2 CHRONICLES 1:2-6;1 KINGS 3:5-15;2 CHRONICLES 1:7-13|05-21:1 KINGS 3:16-28;1 KINGS 5:1-18;2 CHRONICLES 2:1-18;1 KINGS 6:1-13;2 CHRONICLES 3:1-14;1 KINGS 6:14-38|05-22:1 KINGS 7:1-51;2 CHRONICLES 3:15-4:22|05-23:1 KINGS 8:1-11;2 CHRONICLES 5:1-14;1 KINGS 8:12-21;2 CHRONICLES 6:1-11;1 KINGS 8:22-53;2 CHRONICLES 6:12-42|05-24:1 KINGS 8:54-66;2 CHRONICLES 7:1-10;1 KINGS 9:1-9;2 CHRONICLES 7:11-22;1 KINGS 9:10-14|05-25:2 CHRONICLES 8:1-18;1 KINGS 9:15-10:13;2 CHRONICLES 9:1-12;1 KINGS 10:14-29;2 CHRONICLES 9:13-28;2 CHRONICLES 1:14-17|05-26:1 KINGS 4:1-34;PSALMS 72;PSALMS 127|05-27:PROVERBS 1:1-4:27|05-28:PROVERBS 5:1-7:27|05-29:PROVERBS 8:1-10:32|05-30:PROVERBS 11:1-13:25|05-31:PROVERBS 14:1-16:33|06-01:PROVERBS 17:1-19:29|06-02:PROVERBS 20:1-22:16|06-03:PROVERBS 22:17-24:34|06-04:SONG OF SOLOMON 1:1-8:14|06-05:1 KINGS 11:1-43;2 CHRONICLES 9:29-31;ECCLESIASTES 1:1-11|06-06:ECCLESIASTES 1:12-6:12|06-07:ECCLESIASTES 7:1-11:6|06-08:ECCLESIASTES 11:7-12:14;1 KINGS 12:1-20;2 CHRONICLES 10:1-19;1 KINGS 12:21-24;2 CHRONICLES 11:1-4;1 KINGS 12:25-33;2 CHRONICLES 11:5-17|06-09:1 KINGS 13:1-14:20;2 CHRONICLES 11:18-23;1 KINGS 14:21-24;2 CHRONICLES 12:1-12;1 KINGS 14:25-28;2 CHRONICLES 12:13-16;1 KINGS 14:29-15:5;2 CHRONICLES 13:1-22;1 KINGS 15:6-8;2 CHRONICLES 14:1-8;1 KINGS 15:9-15;2 CHRONICLES 14:9-15:19|06-10:1 KINGS 15:16-22;2 CHRONICLES 16:1-10;1 KINGS 15:25-16:34;1 KINGS 15:23-24;2 CHRONICLES 16:11-17:19;1 KINGS 17:1-7|06-11:1 KINGS 17:8-20:22|06-12:1 KINGS 20:23-22:9;2 CHRONICLES 18:1-8|06-13:1 KINGS 22:10-28;2 CHRONICLES 18:9-27;1 KINGS 22:29-35;2 CHRONICLES 18:28-34;1 KINGS 22:36-40;1 KINGS 22:51-53;2 CHRONICLES 19:1-20:30|06-14:2 KINGS 1:1-18;2 KINGS 3:1-27;1 KINGS 22:41-49;2 CHRONICLES 20:31-37;1 KINGS 22:50;2 CHRONICLES 21:1-4;2 KINGS 8:16-22;2 CHRONICLES 21:5-7|06-15:2 KINGS 2:1-25;2 KINGS 4:1-44|06-16:2 KINGS 5:1-8:15|06-17:2 CHRONICLES 21:8-20;2 KINGS 8:23-29;2 CHRONICLES 22:1-7;2 KINGS 9:1-10:17;2 CHRONICLES 22:8-9;2 KINGS 10:18-31|06-18:2 KINGS 11:1-3;2 CHRONICLES 22:10-12;2 KINGS 11:4-12;2 CHRONICLES 23:1-11;2 KINGS 11:13-16;2 CHRONICLES 23:12-15;2 KINGS 11:17-21;2 CHRONICLES 23:16-21;2 KINGS 12:1-16;2 CHRONICLES 24:1-22;2 KINGS 10:32-36|06-19:2 KINGS 13:1-11;2 KINGS 12:17-21;2 CHRONICLES 24:23-27;2 KINGS 13:14-25|06-20:2 KINGS 14:1-14;2 CHRONICLES 25:1-24;2 KINGS 13:12-13;2 KINGS 14:15-16;2 KINGS 14:23-27;2 CHRONICLES 25:25-28;2 KINGS 14:17-22;2 KINGS 15:1-5;2 CHRONICLES 26:1-21;JONAH 1:1-4:11|06-21:AMOS 1:1-6:14|06-22:AMOS 7:1-9:15;2 KINGS 14:28-29;2 KINGS 15:8-29;2 KINGS 15:6-7;2 CHRONICLES 26:22-23;ISAIAH 6:1-13|06-23:2 KINGS 15:32-38;2 CHRONICLES 27:1-9;MICAH 1:1-16;2 KINGS 16:1-9;2 CHRONICLES 28:1-15;ISAIAH 7:1-25|06-24:ISAIAH 8:1-11:16|06-25:ISAIAH 12:1-6;ISAIAH 17:1-14;2 CHRONICLES 28:16-21;2 KINGS 16:10-18;2 CHRONICLES 28:22-25;2 KINGS 18:1-8;2 CHRONICLES 29:1-2;2 KINGS 15:30-31;2 KINGS 17:1-4;HOSEA 1:1-2:13|06-26:HOSEA 2:14-8:14|06-27:HOSEA 9:1-14:9|06-28:ISAIAH 28:1-29;2 KINGS 17:5;2 KINGS 18:9-12;2 KINGS 17:6-41;ISAIAH 1:1-20|06-29:ISAIAH 1:21-5:30|06-30:2 KINGS 16:19-20;2 CHRONICLES 28:26-27;ISAIAH 13:1-16:14|07-01:2 CHRONICLES 29:3-31:21|07-02:PROVERBS 25:1-29:27|07-03:PROVERBS 30:1-31:31|07-04:PSALMS 42-46|07-05:PSALMS 47-49;PSALMS 84-85;PSALMS 87|07-06:PSALMS 1-2;PSALMS 10;PSALMS 33;PSALMS 71;PSALMS 91|07-07:PSALMS 92-97|07-08:PSALMS 98-100;PSALMS 102;PSALMS 104|07-09:PSALMS 105-106|07-10:PSALMS 107;PSALMS 111-114|07-11:PSALMS 115-118|07-12:PSALMS 119|07-13:PSALMS 120-121;PSALMS 123;PSALMS 125-126|07-14:PSALMS 128-130;PSALMS 132;PSALMS 134-135|07-15:PSALMS 136;PSALMS 146-150|07-16:ISAIAH 18:1-23:18|07-17:ISAIAH 24:1-27:13;ISAIAH 29:1-24|07-18:ISAIAH 30:1-33:24|07-19:ISAIAH 34:1-35:10;MICAH 2:1-5:15|07-20:MICAH 6:1-7:20;2 CHRONICLES 32:1-8;2 KINGS 18:13-18;ISAIAH 36:1-3;2 KINGS 18:19-37;ISAIAH 36:4-22|07-21:2 KINGS 19:1-19;ISAIAH 37:1-20;2 CHRONICLES 32:9-19;2 KINGS 19:20-37;ISAIAH 37:21-38;2 CHRONICLES 32:20-23|07-22:2 KINGS 20:1-11;ISAIAH 38:1-8;2 CHRONICLES 32:24-31;ISAIAH 38:9-22;2 KINGS 20:12-19;ISAIAH 39:1-8|07-23:ISAIAH 40:1-44:5|07-24:ISAIAH 44:6-48:11|07-25:ISAIAH 48:12-52:12|07-26:ISAIAH 52:13-57:21|07-27:ISAIAH 58:1-63:14|07-28:ISAIAH 63:15-66:24;2 KINGS 20:20-21;2 CHRONICLES 32:32-33|07-29:2 KINGS 21:1-9;2 CHRONICLES 33:1-9;2 KINGS 21:10-17;2 CHRONICLES 33:10-19;2 KINGS 21:18;2 CHRONICLES 33:20;2 KINGS 21:19-26;2 CHRONICLES 33:21-25;2 KINGS 22:1-2;2 CHRONICLES 34:1-7;JEREMIAH 1:1-2:22|07-30:JEREMIAH 2:23-5:19|07-31:JEREMIAH 5:20-6:30;2 KINGS 22:3-20;2 CHRONICLES 34:8-28|08-01:2 KINGS 23:1-20;2 CHRONICLES 34:29-33;2 KINGS 23:21-28;2 CHRONICLES 35:1-19;NAHUM 1:1-3:19|08-02:HABAKKUK 1:1-3:19;ZEPHANIAH 1:1-2:7|08-03:ZEPHANIAH 2:8-3:20;2 CHRONICLES 35:20-27;2 KINGS 23:29-30;JEREMIAH 47:1-48:47|08-04:2 CHRONICLES 36:1-4;2 KINGS 23:31-37;2 CHRONICLES 36:5;JEREMIAH 22:1-23;JEREMIAH 26:1-24;2 KINGS 24:1-4;JEREMIAH 25:1-14|08-05:JEREMIAH 25:15-38;JEREMIAH 36:1-32;JEREMIAH 45:1-46:28|08-06:JEREMIAH 19:1-20:18;DANIEL 1:1-21|08-07:DANIEL 2:1-3:30;JEREMIAH 7:1-8:3|08-08:JEREMIAH 8:4-11:23|08-09:JEREMIAH 12:1-15:21|08-10:JEREMIAH 16:1-18:23;JEREMIAH 35:1-19|08-11:JEREMIAH 49:1-33;2 KINGS 24:5-7;2 CHRONICLES 36:6-8;2 KINGS 24:8-9;2 CHRONICLES 36:9;JEREMIAH 22:24-23:32|08-12:JEREMIAH 23:33-24:10;JEREMIAH 29:1-31:14|08-13:JEREMIAH 31:15-40;JEREMIAH 49:34-51:14|08-14:JEREMIAH 51:15-58;2 CHRONICLES 36:10;2 KINGS 24:10-17;1 CHRONICLES 3:10-16;2 CHRONICLES 36:11-14;JEREMIAH 52:1-3;2 KINGS 24:18-20;JEREMIAH 37:1-10|08-15:JEREMIAH 37:11-38:28;EZEKIEL 1:1-3:15|08-16:EZEKIEL 3:16-4:17;JEREMIAH 27:1-28:17;JEREMIAH 51:59-64|08-17:EZEKIEL 5:1-9:11|08-18:EZEKIEL 10:1-13:23|08-19:EZEKIEL 14:1-16:63|08-20:EZEKIEL 17:1-19:14|08-21:EZEKIEL 20:1-22:16|08-22:EZEKIEL 22:17-23:49;2 KINGS 24:20-25:2;JEREMIAH 52:3-5;JEREMIAH 39:1;EZEKIEL 24:1-14|08-23:EZEKIEL 24:15-25:17;JEREMIAH 34:1-22;JEREMIAH 21:1-14;EZEKIEL 29:1-16;EZEKIEL 30:20-31:18|08-24:JEREMIAH 32:1-33:26;EZEKIEL 26:1-14|08-25:EZEKIEL 26:15-28:26;2 KINGS 25:3-7;JEREMIAH 52:6-11;JEREMIAH 39:2-10|08-26:JEREMIAH 39:11-18;JEREMIAH 40:1-6;2 KINGS 25:8-21;JEREMIAH 52:12-27;2 CHRONICLES 36:15-21;LAMENTATIONS 1:1-22|08-27:LAMENTATIONS 2:1-4:22|08-28:LAMENTATIONS 5:1-22;OBADIAH 1:1-21;2 KINGS 25:22-26;JEREMIAH 40:7-41:18|08-29:JEREMIAH 42:1-44:30;EZEKIEL 33:21-33|08-30:EZEKIEL 34:1-36:38|08-31:EZEKIEL 37:1-39:29;EZEKIEL 32:1-16|09-01:EZEKIEL 32:17-33:20;JEREMIAH 52:28-30;PSALMS 137;1 CHRONICLES 4:24-5:17|09-02:1 CHRONICLES 5:18-26;1 CHRONICLES 6:3;1 CHRONICLES 6:49;1 CHRONICLES 6:4-15|09-03:1 CHRONICLES 8:29-9:1;DANIEL 4:1-37;EZEKIEL 40:1-37|09-04:EZEKIEL 40:38-43:27|09-05:EZEKIEL 44:1-46:24|09-06:EZEKIEL 47:1-48:35;EZEKIEL 29:17-30:19;2 KINGS 25:27-30;JEREMIAH 52:31-34|09-07:DANIEL 7:1-8:27;DANIEL 5:1-31|09-08:DANIEL 6:1-28;DANIEL 9:1-27;2 CHRONICLES 36:22-23;EZRA 1:1-11;1 CHRONICLES 3:17-19|09-09:EZRA 2:1-4:5;1 CHRONICLES 3:19-24|09-10:DANIEL 10:1-12:13;EZRA 4:24-5:1;HAGGAI 1:1-15|09-11:HAGGAI 2:1-9;ZECHARIAH 1:1-6;HAGGAI 2:10-19;EZRA 5:2;HAGGAI 2:20-23;ZECHARIAH 1:7-5:11|09-12:ZECHARIAH 6:1-15;EZRA 5:3-6:14;ZECHARIAH 7:1-8:23|09-13:ZECHARIAH 9:1-14:21|09-14:EZRA 6:14-22;EZRA 4:6;ESTHER 1:1-4:17|09-15:ESTHER 5:1-10:3|09-16:EZRA 4:7-23;EZRA 7:1-8:36|09-17:EZRA 9:1-10:44;NEHEMIAH 1:1-2:20|09-18:NEHEMIAH 3:1-7:3|09-19:NEHEMIAH 7:4-8:12|09-20:NEHEMIAH 8:13-10:39|09-21:NEHEMIAH 11:1-12:26;1 CHRONICLES 9:1-34|09-22:NEHEMIAH 12:27-13:6;NEHEMIAH 5:14-19;NEHEMIAH 13:7-31;MALACHI 1:1-2:9|09-23:MALACHI 2:10-4:6;JOEL 1:1-3:21|09-24:MARK 1:1;LUKE 1:1-4;JOHN 1:1-18;MATTHEW 1:1-17;LUKE 3:23-38;LUKE 1:5-38|09-25:LUKE 1:39-80;MATTHEW 1:18-25;LUKE 2:1-40|09-26:MATTHEW 2:1-23;LUKE 2:41-52;MARK 1:2-8;MATTHEW 3:1-12;LUKE 3:1-18;MARK 1:9-11;MATTHEW 3:13-17;LUKE 3:21-22|09-27:MARK 1:12-13;MATTHEW 4:1-11;LUKE 4:1-15;JOHN 1:19-2:25|09-28:JOHN 3:1-4:45;LUKE 3:19-20|09-29:MARK 1:14-15;MATTHEW 4:12-17;JOHN 4:46-54;LUKE 4:16-30;MARK 1:16-20;MATTHEW 4:18-22;MARK 1:21-28;LUKE 4:31-37;MARK 1:29-34;MATTHEW 8:14-17;LUKE 4:38-41;MARK 1:35-39;LUKE 4:42-44;MATTHEW 4:23-25|09-30:LUKE 5:1-11;MARK 1:40-45;MATTHEW 8:1-4;LUKE 5:12-16;MARK 2:1-12;MATTHEW 9:1-8;LUKE 5:17-26;MARK 2:13-17;MATTHEW 9:9-13;LUKE 5:27-32;MARK 2:18-22;MATTHEW 9:14-17;LUKE 5:33-39|10-01:JOHN 5:1-47;MARK 2:23-28;MATTHEW 12:1-8;LUKE 6:1-5;MARK 3:1-6;MATTHEW 12:9-14;LUKE 6:6-11;MATTHEW 12:15-21|10-02:MARK 3:7-19;LUKE 6:12-16;MATTHEW 5:1-12;LUKE 6:17-26;MATTHEW 5:13-48;LUKE 6:27-36;MATTHEW 6:1-4|10-03:MATTHEW 6:5-7:6;LUKE 6:37-42;MATTHEW 7:7-20;LUKE 6:43-45;MATTHEW 7:21-29;LUKE 6:46-49|10-04:MATTHEW 8:5-13;LUKE 7:1-17;MATTHEW 11:1-19;LUKE 7:18-35;MATTHEW 11:20-30;LUKE 7:36-50|10-05:LUKE 8:1-3;MARK 3:20-30;MATTHEW 12:22-45;MARK 3:31-35;MATTHEW 12:46-50;LUKE 8:19-21;MARK 4:1-9;MATTHEW 13:1-9;LUKE 8:4-8;MARK 4:10-20|10-06:MATTHEW 13:10-23;LUKE 8:9-18;MARK 4:21-29;MATTHEW 13:24-30;MARK 4:30-34;MATTHEW 13:31-52;MARK 4:35-41;MATTHEW 8:23-27;LUKE 8:22-25|10-07:MARK 5:1-20;MATTHEW 8:28-34;LUKE 8:26-39;MARK 5:21-43;MATTHEW 9:18-26;LUKE 8:40-56|10-08:MATTHEW 9:27-34;MARK 6:1-6;MATTHEW 13:53-58;MATTHEW 9:35-38;MARK 6:7-13;MATTHEW 10:1-42;LUKE 9:1-6|10-09:LUKE 9:7-9;MARK 6:14-29;MATTHEW 14:1-21;MARK 6:30-44;LUKE 9:10-17;JOHN 6:1-15;MARK 6:45-52;MATTHEW 14:22-33;JOHN 6:16-21;MARK 6:53-56;MATTHEW 14:34-36|10-10:JOHN 6:22-71;MARK 7:1-23;MATTHEW 15:1-20|10-11:MARK 7:24-30;MATTHEW 15:21-28;MARK 7:31-37;MATTHEW 15:29-31;MARK 8:1-10;MATTHEW 15:32-16:4;MARK 8:11-21;MATTHEW 16:5-12|10-12:MARK 8:22-30;MATTHEW 16:13-20;LUKE 9:18-20;MARK 8:31-9:1;MATTHEW 16:21-28;LUKE 9:21-27;MARK 9:2-13;MATTHEW 17:1-13;LUKE 9:28-36|10-13:MARK 9:14-29;MATTHEW 17:14-21;LUKE 9:37-43;MARK 9:30-32;MATTHEW 17:22-23;LUKE 9:43-45;MATTHEW 17:24-27;MARK 9:33-37;MATTHEW 18:1-6;LUKE 9:46-48;MARK 9:38-41;LUKE 9:49-50;MARK 9:42-50;MATTHEW 18:7-35|10-14:JOHN 7:1-9;LUKE 9:51-56;MATTHEW 8:18-22;LUKE 9:57-62;JOHN 7:10-8:20|10-15:JOHN 8:21-59;LUKE 10:1-11:13|10-16:LUKE 11:14-12:34|10-17:LUKE 12:35-13:21;JOHN 9:1-41|10-18:JOHN 10:1-42;LUKE 13:22-14:24|10-19:LUKE 14:25-17:10;JOHN 11:1-37|10-20:JOHN 11:38-57;LUKE 17:11-18:8|10-21:LUKE 18:9-14;MARK 10:1-12;MATTHEW 19:1-12;MARK 10:13-16;MATTHEW 19:13-15;LUKE 18:15-17;MARK 10:17-31;MATTHEW 19:16-30;LUKE 18:18-30|10-22:MATTHEW 20:1-16;MARK 10:32-34;MATTHEW 20:17-19;LUKE 18:31-34;MARK 10:35-45;MATTHEW 20:20-34;MARK 10:46-52;LUKE 18:35-19:27|10-23:MARK 14:3-9;MATTHEW 26:6-13;JOHN 12:1-11;MARK 11:1-11;MATTHEW 21:1-11;LUKE 19:28-40;JOHN 12:12-19;LUKE 19:41-44;JOHN 12:20-36|10-24:JOHN 12:37-50;MARK 11:12-14;MATTHEW 21:18-22;MARK 11:15-19;MATTHEW 21:12-17;LUKE 19:45-48;MARK 11:20-33;MATTHEW 21:23-27;LUKE 20:1-8|10-25:MATTHEW 21:28-32;MARK 12:1-12;MATTHEW 21:33-46;LUKE 20:9-19;MATTHEW 22:1-14;MARK 12:13-17;MATTHEW 22:15-22;LUKE 20:20-26;MARK 12:18-27;MATTHEW 22:23-33;LUKE 20:27-40|10-26:MARK 12:28-34;MATTHEW 22:34-40;MARK 12:35-37;MATTHEW 22:41-46;LUKE 20:41-44;MARK 12:38-40;MATTHEW 23:1-12;LUKE 20:45-47;MATTHEW 23:13-39;MARK 12:41-44;LUKE 21:1-4|10-27:MARK 13:1-23;MATTHEW 24:1-25;LUKE 21:5-24;MARK 13:24-31;MATTHEW 24:26-35;LUKE 21:25-33|10-28:MARK 13:32-37;MATTHEW 24:36-51;LUKE 21:34-38;MATTHEW 25:1-46|10-29:MARK 14:1-2;MATTHEW 26:1-5;LUKE 22:1-2;MARK 14:10-11;MATTHEW 26:14-16;LUKE 22:3-6;MARK 14:12-16;MATTHEW 26:17-19;LUKE 22:7-13;JOHN 13:1-20;MARK 14:17-26;MATTHEW 26:20-30;LUKE 22:14-30;JOHN 13:21-30|10-30:JOHN 13:31-38;MARK 14:27-31;MATTHEW 26:31-35;LUKE 22:31-38;JOHN 14:1-15:17|10-31:JOHN 15:18-17:26|11-01:JOHN 18:1-2;MARK 14:32-42;MATTHEW 26:36-46;LUKE 22:39-46;MARK 14:43-52;MATTHEW 26:47-56;LUKE 22:47-53;JOHN 18:3-24|11-02:MARK 14:53-65;MATTHEW 26:57-68;MARK 14:66-72;MATTHEW 26:69-75;LUKE 22:54-65;JOHN 18:25-27;MARK 15:1;MATTHEW 27:1-2;LUKE 22:66-71;MATTHEW 27:3-10|11-03:MARK 15:2-5;MATTHEW 27:11-14;LUKE 23:1-12;JOHN 18:28-40;MARK 15:6-15;MATTHEW 27:15-26;LUKE 23:13-25;JOHN 19:1-16;MARK 15:16-20;MATTHEW 27:27-31|11-04:MARK 15:21-24;MATTHEW 27:32-34;LUKE 23:26-31;JOHN 19:17;MARK 15:25-32;MATTHEW 27:35-44;LUKE 23:32-43;JOHN 19:18-27;MARK 15:33-41;MATTHEW 27:45-56;LUKE 23:44-49;JOHN 19:28-37|11-05:MARK 15:42-47;MATTHEW 27:57-61;LUKE 23:50-56;JOHN 19:38-42;MATTHEW 27:62-66;MARK 16:1-8;MATTHEW 28:1-7;LUKE 24:1-12;MARK 16:9-11;JOHN 20:1-18;MATTHEW 28:8-15|11-06:LUKE 24:13-43;MARK 16:12-13;JOHN 20:19-23;MARK 16:14;JOHN 20:24-21:25;MATTHEW 28:16-20;MARK 16:15-18;LUKE 24:44-49|11-07:MARK 16:19-20;LUKE 24:50-53;ACTS 1:1-2:47|11-08:ACTS 3:1-5:42|11-09:ACTS 6:1-8:1|11-10:ACTS 8:1-9:43|11-11:ACTS 10:1-12:5|11-12:ACTS 12:6-14:20|11-13:ACTS 14:21-28;GALATIANS 1:1-3:23|11-14:GALATIANS 3:24-6:18;ACTS 15:1-21|11-15:ACTS 15:22-17:15|11-16:ACTS 17:16-18:3;1 THESSALONIANS 1:1-5:11|11-17:1 THESSALONIANS 5:12-28;2 THESSALONIANS 1:1-3:18;ACTS 18:4-23|11-18:ACTS 18:24-19:20;1 CORINTHIANS 1:1-3:23|11-19:1 CORINTHIANS 4:1-7:40|11-20:1 CORINTHIANS 8:1-11:1|11-21:1 CORINTHIANS 11:2-13:13|11-22:1 CORINTHIANS 14:1-15:58|11-23:1 CORINTHIANS 16:1-24;ACTS 19:21-20:3;ROMANS 1:1-32|11-24:ROMANS 2:1-4:25|11-25:ROMANS 5:1-8:17|11-26:ROMANS 8:18-10:21|11-27:ROMANS 11:1-14:23|11-28:ROMANS 15:1-16:27;2 CORINTHIANS 1:1-2:4|11-29:2 CORINTHIANS 2:5-6:13|11-30:2 CORINTHIANS 6:14-10:18|12-01:2 CORINTHIANS 11:1-13:14;ACTS 20:4-6|12-02:ACTS 20:7-21:36|12-03:ACTS 21:37-23:35|12-04:ACTS 24:1-26:32|12-05:ACTS 27:1-44|12-06:ACTS 28:1-31;EPHESIANS 1:1-2:22|12-07:EPHESIANS 3:1-5:14|12-08:EPHESIANS 5:15-6:24;COLOSSIANS 1:1-23|12-09:COLOSSIANS 1:24-4:18|12-10:PHILEMON 1:1-25;PHILIPPIANS 1:1-2:11|12-11:PHILIPPIANS 2:12-4:23|12-12:JAMES 1:1-3:18|12-13:JAMES 4:1-5:20;1 TIMOTHY 1:1-2:15|12-14:1 TIMOTHY 3:1-6:10|12-15:1 TIMOTHY 6:11-21;TITUS 1:1-3:15;2 TIMOTHY 1:1-18|12-16:2 TIMOTHY 2:1-4:18|12-17:2 TIMOTHY 4:19-22;HEBREWS 1:1-4:13|12-18:HEBREWS 4:14-7:28|12-19:HEBREWS 8:1-10:39|12-20:HEBREWS 11:1-12:29|12-21:HEBREWS 13:1-25;1 PETER 1:1-2:3|12-22:1 PETER 2:4-5:11|12-23:1 PETER 5:12-14;2 PETER 1:1-3:18|12-24:1 JOHN 1:1-4:6|12-25:1 JOHN 4:7-5:21;2 JOHN 1:1-13;3 JOHN 1:1-15|12-26:JUDE 1:1-25;REVELATION 1:1-2:29|12-27:REVELATION 3:1-6:17|12-28:REVELATION 7:1-10:11|12-29:REVELATION 11:1-14:20|12-30:REVELATION 15:1-18:24|12-31:REVELATION 19:1-22:21';
  var OYB={};OYB_RAW.split('|').forEach(function(x){var i=x.indexOf(':');OYB[x.slice(0,i)]=x.slice(i+1);});
  function chapters(book){var b=BOOKS.find(function(x){return x[0].toLowerCase()===String(book||'').toLowerCase();});return b?b[1]:0;}
  function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
  function todayKey(){try{return dk(new Date());}catch(e){return new Date().toISOString().slice(0,10);}}
  function ls(k,v){try{if(v===undefined){var r=localStorage.getItem(k);return r?JSON.parse(r):null;}if(v===null)localStorage.removeItem(k);else localStorage.setItem(k,JSON.stringify(v));}catch(e){return null;}}
  function weekKey(d){d=new Date(d||new Date());var day=(d.getDay()+6)%7;d.setDate(d.getDate()-day);return dk(d);}
  function sessionKey(date){return 'bstudy_'+(date||todayKey());}
  function toast(m){try{if(typeof v26Toast==='function')v26Toast(m);}catch(e){}}
  function titleCase(b){return b.toLowerCase().replace(/(^|\s)([a-z])/g,function(m,a,c){return a+c.toUpperCase();}).replace(/\bOf\b/,'of');}
  function bookName(raw){var b=String(raw||'').replace(/\s+/g,' ').trim(),l=b.toLowerCase();if(l==='psalm')l='psalms';if(l==='song'||l==='song of songs')l='song of solomon';
    var hit=BOOKS.find(function(x){return x[0].toLowerCase()===l;})||BOOKS.find(function(x){return l.length>=3&&x[0].toLowerCase().indexOf(l)===0;});return hit?hit[0]:titleCase(b);}

  // ── Plans ──  {type:'oneyear'} (default) · {type:'book',book,chapter} · {type:'verse'}
  function plan(){var p=ls('bstudy_plan');if(!p)return {type:'oneyear'};if(!p.type)p.type=p.book?'book':'oneyear';return p;}
  function mmdd(date){return String(date).slice(5,10);}
  function oybFixes(){return ls('bstudy_oyb_fix')||{};}
  function oybReading(md){return oybFixes()[md]||OYB[md]||'';}

  // ── Passages: "ACTS 17:16-18:3; 1 THESSALONIANS 1:1-5:11" → segments ──
  function parseSeg(t){
    var m=String(t||'').trim().match(/^((?:[1-3]\s*)?[A-Za-z][A-Za-z .]*?)\s+(\d+)(?::(\d+))?(?:\s*[-–]\s*(\d+)(?::(\d+))?)?\s*$/);if(!m)return null;
    var b=bookName(m[1]),c1=+m[2],v1=m[3]?+m[3]:0,c2=c1,v2=0;
    if(m[4]){if(m[5]){c2=+m[4];v2=+m[5];}else if(v1){v2=+m[4];}else{c2=+m[4];}}else if(v1)v2=v1;
    return {b:b,c1:c1,v1:v1,c2:c2,v2:v2};
  }
  function parseSegs(text){return String(text||'').split(/\s*;\s*/).map(parseSeg).filter(Boolean);}
  function segLabel(s){return s.b+' '+s.c1+(s.v1?':'+s.v1:'')+(s.c2!==s.c1?'–'+s.c2+(s.v2?':'+s.v2:''):(s.v2&&s.v2!==s.v1?'–'+s.v2:''));}
  function segQuery(s){return s.b+' '+s.c1+(s.v1?':'+s.v1:'')+(s.c2!==s.c1||(s.v2&&s.v2!==s.v1)?'-'+(s.c2!==s.c1?s.c2+(s.v2?':'+s.v2:''):s.v2):'');}
  var cache={};
  // One whole chapter at a time (bible-api.com rejects long ranges like "1 Thessalonians 1:1-5:11"), cached; then trimmed
  // to the segment. A different spelling of the book is tried if the first fails.
  function getJSON(u){return fetch(u).then(function(r){if(!r.ok){var e=new Error('HTTP '+r.status);e.status=r.status;throw e;}return r.json();});}
  function fetchChapter(book,ch){
    var k=book+' '+ch;if(cache[k])return Promise.resolve(cache[k]);
    function url(name){return 'https://bible-api.com/'+encodeURIComponent(name+' '+ch).replace(/%20/g,'+')+'?translation=web';}
    var alt=book.replace(/^([1-3]) /,'$1').replace('Song of Solomon','Song of Songs');
    return getJSON(url(book)).catch(function(e){if(alt!==book)return getJSON(url(alt));throw e;})
      .then(function(j){var v=(j.verses||[]).map(function(x){return {b:book,c:x.chapter||ch,v:x.verse,t:String(x.text||'').replace(/\s+/g,' ').trim()};});cache[k]=v;return v;});
  }
  function fetchSeg(s){
    var chs=[];for(var c=s.c1;c<=s.c2;c++)chs.push(c);
    return Promise.all(chs.map(function(c){return fetchChapter(s.b,c);})).then(function(lists){
      var out=[];lists.forEach(function(list){list.forEach(function(v){var a=v.c*1000+v.v;
        if(a>=s.c1*1000+(s.v1||1)&&a<=s.c2*1000+(s.v2||999))out.push(v);});});
      if(!out.length)throw new Error('Scripture lookup failed for '+segLabel(s));return out;
    }).catch(function(e){throw new Error('Scripture lookup failed for '+segLabel(s)+(e.status?' ('+e.status+')':''));});
  }
  // A passage that can't be loaded doesn't stop the study: its list is empty and "Read in NLT" covers it.
  function fetchAll(segs){return Promise.all(segs.map(function(sg){return fetchSeg(sg).catch(function(){return [];});}));}
  function nltLink(segs){return 'https://www.biblegateway.com/passage/?search='+encodeURIComponent(segs.map(segLabel).join('; ').replace(/–/g,'-'))+'&version=NLT';}

  // ── AI ──
  function ai(prompt,maxTok){
    var body={model:'claude-sonnet-4-5',max_tokens:maxTok||2500,messages:[{role:'user',content:prompt}]};
    return journalProtectedFunction(body).then(function(d){if(d&&d.error){body.model='claude-haiku-4-5-20251001';return journalProtectedFunction(body);}return d;})
      .then(function(d){if(!d||d.error)throw new Error((d&&d.error&&d.error.message)||'AI unavailable');return String((d.content&&d.content[0]&&d.content[0].text)||'').trim();});
  }
  function json(t){var m=String(t).match(/\{[\s\S]*\}/);return JSON.parse(m?m[0]:t);}
  function tick(key){if(S&&S.date&&S.date!==todayKey())return;try{   // a past study (opened from the Journal) never ticks today's boxes
var box=document.querySelector('.tri-check[data-key="'+key+'"]');if(!box)return;if(box.dataset.state!=='green'){applyTriState(box,'green');try{save();}catch(e){}try{renderDailyCheckOverview();}catch(e){}}}catch(e){}}

  // ── What to study for a date ──
  function readingFor(date,dayOverride){
    var p=plan();
    if(p.type==='oneyear'){var md=dayOverride||mmdd(date);return {source:'oneyear',day:md,text:oybReading(md),label:'One Year Chronological · '+new Date('2026-'+md+'T12:00').toLocaleDateString('en-US',{month:'long',day:'numeric'})};}
    if(p.type==='book'&&p.book)return {source:'book',text:p.book+' '+p.chapter,label:'Reading plan · '+p.book+' '+p.chapter};
    var verse='';try{verse=(document.getElementById('spDailyBreadText')||{}).value||localStorage.getItem('medit_scripture')||'';}catch(e){}
    var ref=(verse.match(/((?:[1-3]\s*)?[A-Za-z][A-Za-z ]+\s+\d+:\d+)/)||[])[1];var sg=ref&&parseSeg(ref);
    if(sg)return {source:'verse',text:sg.b+' '+sg.c1,key:{b:sg.b,c:sg.c1,v:sg.v1},label:'Daily Bread · '+segLabel(sg)};
    return {source:'verse',text:'Psalms 46',key:{b:'Psalms',c:46,v:10},label:'Psalm 46'};
  }

  // ── Session ──
  var S=null; // {date, source, day, text, label, segs, key{b,c,v}, focus{b,c1,v1,c2,v2}, study, answers{s,o[],a[],p}, step, done{}, finished, closing}
  function persist(){if(!S)return;var c=Object.assign({},S);delete c.verses;delete c._preparing;delete c._closing;delete c._err;delete c._x;delete c._loading;ls(sessionKey(S.date),c);}
  function open(date,day){
    if(!journalAIKey()){alert('Add your Anthropic API key in Settings first.');return;}
    date=date||todayKey();
    var saved=ls(sessionKey(date));
    if(saved&&saved.segs&&!day){S=saved;}
    else{var r=readingFor(date,day||((ls('bstudy_day')||{}).date===date?(ls('bstudy_day')||{}).day:''));S={date:date,source:r.source,day:r.day||'',text:r.text,label:r.label,segs:parseSegs(r.text),key:r.key||null,focus:null,study:null,answers:{s:'',o:[],a:[],p:''},step:0,done:{}};}
    if(!S.segs.length){alert('No reading found for that day. Use ✏️ Fix reading on the Bible Study card.');S=null;return;}
    build();render();
  }
  function build(){
    var ov=document.getElementById('bstudyOverlay');if(ov)ov.remove();
    ov=document.createElement('div');ov.id='bstudyOverlay';
    ov.innerHTML='<div class="bs-sheet"><div class="bs-top"><button class="bs-x" onclick="bibleStudy.close()">✕</button><div class="bs-title">📖 Bible Study</div><div class="bs-ref" id="bsRef"></div></div>'
      +'<div class="bs-steps" id="bsSteps"></div><div class="bs-body" id="bsBody"></div><div class="bs-nav"><button id="bsBack" class="bs-btn2" onclick="bibleStudy.step(-1)">‹ Back</button><button id="bsNext" class="bs-btn" onclick="bibleStudy.step(1)">Next ›</button></div></div>';
    document.body.appendChild(ov);document.body.style.overflow='hidden';
  }
  var STEPS=['🙏 Open','📖 Read','🔍 Study','🎶 Worship','🙏 Close'];
  function refText(){return S.segs.map(segLabel).join('; ');}
  function isKey(v){return S.key&&v.b===S.key.b&&v.c===S.key.c&&v.v===S.key.v;}
  function inFocus(v){var f=S.focus;if(!f||v.b!==f.b)return false;var a=v.c*1000+v.v;return a>=f.c1*1000+f.v1&&a<=f.c2*1000+(f.v2||999);}
  function render(){
    if(!S)return;var body=document.getElementById('bsBody');if(!body)return;
    document.getElementById('bsRef').innerHTML=esc(S.label)+'<br><span style="opacity:.8">'+esc(refText())+'</span>';
    document.getElementById('bsSteps').innerHTML=STEPS.map(function(s,i){return '<span class="'+(i===S.step?'on':(S.done[i]?'ok':''))+'" onclick="bibleStudy.go('+i+')">'+(S.done[i]&&i!==S.step?'✓ ':'')+s+'</span>';}).join('');
    document.getElementById('bsBack').style.visibility=S.step>0?'visible':'hidden';
    document.getElementById('bsNext').textContent=S.step===4?(S.finished?'Done':'✓ Finish study'):'Next ›';
    if(!S.study){body.innerHTML='<div class="bs-load">⏳ Preparing today\'s study of '+esc(refText())+'…</div>';prepare();return;}
    var st=S.study,h='';
    if(S.step===0){
      h='<div class="bs-card bs-pray"><div class="bs-h">Opening prayer</div><div class="bs-text">'+esc(st.open_prayer||'')+'</div></div>'
        +'<div class="bs-note">Today\'s theme: <b>'+esc(st.theme||'')+'</b>'+(st.focus_label?' · Focus passage: <b>'+esc(st.focus_label)+'</b>':'')+'</div>'
        +(st.overview?'<div class="bs-card"><div class="bs-h">Today\'s reading at a glance</div><div class="bs-text">'+esc(st.overview)+'</div></div>':'');
    }else if(S.step===1){
      h='<div class="bs-row"><button class="bs-btn2" id="bsListen" onclick="bibleStudy.listen()">🎧 Read aloud</button><button class="bs-btn2" onclick="bibleStudy.listenedApp()">'+(S.done.audio?'✓ Listened':'✓ I listened in my Bible app')+'</button>'
        +'<a class="bs-btn2" target="_blank" rel="noopener" href="'+nltLink(S.segs)+'">Read in NLT ↗</a></div>'
        +'<div class="bs-note">Already listened to today\'s audiobook? Tap <b>✓ I listened</b> — you don\'t need to read it again; it skips to the study. Text below: World English Bible. The highlighted section is today\'s study focus'+(S.key?'; the gold verse is the key verse':'')+'.</div>';
      if(!S.verses){h+='<div class="bs-load">⏳ Loading today\'s passages…</div>';loadVerses();}
      else h+=S.verses.map(function(list,i){var sg=S.segs[i];if(!list.length)return '<div class="bs-card"><div class="bs-h">'+esc(segLabel(sg))+'</div><div class="bs-note">Couldn\'t load this passage here — <a target="_blank" rel="noopener" href="'+nltLink([sg])+'">read it in the NLT ↗</a>.</div></div>';return '<div class="bs-card bs-scripture"><div class="bs-h">'+esc(segLabel(sg))+'</div>'
        +list.map(function(v,j){var head=(j===0||list[j-1].c!==v.c)&&(sg.c1!==sg.c2||j===0)?'<b class="bs-ch">'+v.c+'</b>':'';return head+'<span id="bsV'+i+'_'+j+'" class="bs-v '+(isKey(v)?'kv':inFocus(v)?'fo':'')+'"><sup>'+v.v+'</sup> '+v.t.split(/\s+/).map(function(w){return '<span class="w">'+esc(w)+'</span>';}).join(' ')+' </span>';}).join('')+'</div>';}).join('');
    }else if(S.step===2){
      var a=S.answers;
      h='<div class="bs-card"><div class="bs-h">Context</div><div class="bs-text">'+esc(st.context||'')+'</div></div>'
        +(st.word?'<div class="bs-card"><div class="bs-h">Key word</div><div class="bs-text"><b>'+esc(st.word.term||'')+'</b>'+(st.word.original?' — <i>'+esc(st.word.original)+'</i>':'')+'<br>'+esc(st.word.meaning||'')+'</div></div>':'')
        +((st.crossrefs||[]).length?'<div class="bs-card"><div class="bs-h">Cross-references</div>'+st.crossrefs.map(function(c,i){return '<div class="bs-xref" onclick="bibleStudy.xref('+i+')">📖 '+esc(c)+'</div><div class="bs-xtext" id="bsX'+i+'"></div>';}).join('')+'</div>':'')
        +'<div class="bs-h2">S — Scripture</div><div class="bs-q">Which verse stood out to you? Write it out.</div><textarea data-k="s" rows="2">'+esc(a.s)+'</textarea>'
        +'<div class="bs-h2">O — Observation</div>'+(st.observe||[]).map(function(q,i){return '<div class="bs-q">'+esc(q)+'</div><textarea data-k="o'+i+'" rows="2">'+esc(a.o[i]||'')+'</textarea>';}).join('')
        +'<div class="bs-h2">A — Application</div>'+(st.apply||[]).map(function(q,i){return '<div class="bs-q">'+esc(q)+'</div><textarea data-k="a'+i+'" rows="2">'+esc(a.a[i]||'')+'</textarea>';}).join('')
        +'<div class="bs-h2">P — Prayer</div><div class="bs-q">Your response to God in your own words.</div><textarea data-k="p" rows="3">'+esc(a.p)+'</textarea>';
    }else if(S.step===3){
      var song=st.song||{};var q=encodeURIComponent((song.title||'')+' '+(song.artist||''));
      h='<div class="bs-card bs-pray"><div class="bs-h">Worship response</div><div class="bs-text"><b>'+esc(song.title||'')+'</b>'+(song.artist?' — '+esc(song.artist):'')+'<br><span class="bs-note">'+esc(song.why||'')+'</span></div>'
        +'<div class="bs-row"><a class="bs-btn" target="_blank" rel="noopener" href="https://www.youtube.com/results?search_query='+q+'">▶ YouTube</a><a class="bs-btn2" target="_blank" rel="noopener" href="https://open.spotify.com/search/'+q+'">Spotify</a></div></div>'
        +'<div class="bs-note">Listen, sing, or sit quietly with the passage for a few minutes.</div>';
    }else{
      h=S.closing?'<div class="bs-card bs-pray"><div class="bs-h">Closing prayer</div><div class="bs-text">'+esc(S.closing)+'</div></div>':'<div class="bs-load">⏳ Writing a closing prayer from what you wrote…</div>';
      var mem=memoryLine();if(mem)h+='<div class="bs-card"><div class="bs-h">This week\'s memory verse</div><div class="bs-text">'+esc(mem)+'</div></div>';
      if(!S.closing)closing();
    }
    body.innerHTML=h;body.scrollTop=0;
    body.querySelectorAll('textarea').forEach(function(t){t.addEventListener('input',function(){var k=t.dataset.k;if(k==='s'||k==='p')S.answers[k]=t.value;else{S.answers[k[0]][+k.slice(1)]=t.value;}persist();});});
  }
  function memoryLine(){var m=ls('bstudy_mem_'+weekKey());return m?('“'+m.text+'” — '+m.ref):'';}
  function loadVerses(){if(S._loading)return;S._loading=true;fetchAll(S.segs).then(function(v){S.verses=v;S._loading=false;render();}).catch(function(e){S._loading=false;S._err=e.message;var b=document.getElementById('bsBody');if(b)b.innerHTML='<div class="bs-load">Couldn\'t load the passages ('+esc(e.message)+').<br><br><a class="bs-btn" target="_blank" rel="noopener" href="'+nltLink(S.segs)+'">Read in NLT ↗</a></div>';});}

  function prepare(){
    if(S._preparing)return;S._preparing=true;
    fetchAll(S.segs).then(function(lists){
      S.verses=lists;
      var text=lists.map(function(list,i){return '['+segLabel(S.segs[i])+'] '+(list.length?list.map(function(v){return v.c+':'+v.v+' '+v.t;}).join(' '):'(text not available here — use your knowledge of this passage)');}).join('\n');
      var hist=[];try{for(var i=1;i<=6;i++){var d=new Date(S.date+'T12:00');d.setDate(d.getDate()-i);var o=ls(sessionKey(dk(d)));if(o&&o.study)hist.push((o.segs||[]).map(segLabel).join('; ')+(o.study.theme?' ('+o.study.theme+')':''));}}catch(e){}
      var many=lists.reduce(function(n,l){return n+l.length;},0)>30;
      var prompt='You are leading a personal daily Bible study for a Christian man (a runner, husband and father) who is reading through the One Year Chronological Bible (NLT). '
        +'Today\'s reading'+(S.source==='oneyear'?' (that plan\'s reading for '+S.label.split('· ')[1]+')':'')+': '+refText()+'.\n'
        +(S.key?'His focus verse: '+S.key.b+' '+S.key.c+':'+S.key.v+'.\n':'')
        +'World English Bible text (chapter:verse):\n'+text.slice(0,16000)+'\n\n'
        +(hist.length?'His recent studies (keep continuity with the chronological story; don\'t repeat questions): '+hist.join(' | ')+'\n':'')
        +'Return ONLY JSON, no other text:\n{"overview":"2-3 sentences: what happens across today\'s whole reading and where it sits in the chronological story",'
        +'"focus":{"book":"","from":"C:V","to":"C:V"},"focus_label":"Book C:V-C:V","key_verse":"Book C:V","theme":"3-6 words",'
        +'"open_prayer":"4-6 sentence prayer asking for understanding of today\'s reading",'
        +'"context":"3-4 sentences on the focus passage: who wrote it, to whom, when, what is happening, why it matters",'
        +'"word":{"term":"an important English word in the focus passage","original":"Hebrew/Greek word with transliteration","meaning":"1-2 sentences"},'
        +'"crossrefs":["Book C:V — 6-10 word note",3 items],"observe":["3-4 questions about what the focus passage actually says"],'
        +'"apply":["2 questions about living it out this week"],"song":{"title":"a real, well-known worship song that fits the theme","artist":"","why":"one sentence"},'
        +'"memory_verse":"Book C:V — one short verse from today\'s reading worth memorizing"}\n'
        +(many?'"focus": the single most important section of 8-25 verses in today\'s reading to study closely. ':'"focus": the whole reading. ')
        +'Write warmly and plainly. Do not quote any copyrighted translation; refer to verses by reference.';
      return ai(prompt,3000).then(function(t){
        var j=json(t);S.study=j;
        var f=j.focus||{};var a=String(f.from||'').split(':'),b=String(f.to||'').split(':');
        if(f.book&&a.length===2)S.focus={b:bookName(f.book),c1:+a[0],v1:+a[1],c2:+(b[0]||a[0]),v2:+(b[1]||0)};
        if(!S.key&&j.key_verse){var k=parseSeg(j.key_verse);if(k)S.key={b:k.b,c:k.c1,v:k.v1};}
        setMemory(j.memory_verse);S._preparing=false;persist();render();
      });
    }).catch(function(e){S._preparing=false;S._err=e.message;var b=document.getElementById('bsBody');if(b)b.innerHTML='<div class="bs-load">Couldn\'t prepare the study: '+esc(e.message)+'<br><br><button class="bs-btn" onclick="bibleStudy.retry()">Try again</button></div>';});
  }
  function setMemory(mv){
    var wk=weekKey(),k='bstudy_mem_'+wk;if(ls(k)||!mv)return;
    var r=parseSeg(String(mv).split(/\s+[—–-]\s+/)[0]);if(!r||!r.v1)return;
    fetchSeg({b:r.b,c1:r.c1,v1:r.v1,c2:r.c1,v2:r.v1}).then(function(vs){var v=vs[0];if(v){ls(k,{ref:r.b+' '+r.c1+':'+r.v1,text:v.t,week:wk});renderCard();}}).catch(function(){});
  }
  function closing(){
    if(S._closing)return;S._closing=true;var a=S.answers;
    var prompt='Write a short, personal closing prayer (5-7 sentences) for someone who just studied '+(S.study.focus_label||refText())+' (theme: '+(S.study.theme||'')+').\n'
      +'What stood out to them: '+(a.s||'(not written)')+'\nTheir application: '+(a.a.filter(Boolean).join(' / ')||'(not written)')+'\nTheir own prayer: '+(a.p||'(not written)')+'\n'
      +'Pray in first person ("Lord, …"), echo their application specifically, and end with a short commitment for today. Plain text only.';
    ai(prompt,700).then(function(t){S.closing=t;S._closing=false;persist();if(S.step===4)render();}).catch(function(){S.closing='Lord, thank You for speaking to me through today\'s reading. Help me live out what I learned. Amen.';S._closing=false;if(S.step===4)render();});
  }

  // ── Actions ──
  function stepTo(i){
    if(!S)return;
    if(i>S.step){S.done[S.step]=true;if(S.step===0)tick('spAIPrayer');if(S.step===3)tick('spWorship');}
    if(i>4){finish();return;}
    S.step=Math.max(0,Math.min(4,i));persist();render();
  }
  function finish(){
    if(!S.finished){
      S.finished=true;S.done[4]=true;S.finishedAt=Date.now();
      tick('spDailyBread');tick('spAIPrayer');tick('spWorship');
      try{var inp=document.getElementById('spDailyBreadText');var kv=S.key?S.key.b+' '+S.key.c+':'+S.key.v:refText();if(inp&&!inp.value.trim()){inp.value=kv;inp.dispatchEvent(new Event('input',{bubbles:true}));}}catch(e){}
      var p=plan();if(p.type==='book'&&S.source==='book'){var n=chapters(p.book);if(p.chapter<n)p.chapter++;else p.done=true;ls('bstudy_plan',p);}
      persist();toast('📖 Bible study saved');
    }
    close();
  }
  var speaking=false;
  // 🎧 Read aloud verse by verse: the verse being read is shaded and scrolled into view, and — where the phone's voice
  // reports word positions — the word being spoken is highlighted too.
  function listen(){
    var b=document.getElementById('bsListen');
    if(!('speechSynthesis' in window)){alert('Read-aloud isn\'t available in this browser.');return;}
    if(speaking){speechSynthesis.cancel();speaking=false;clearMarks();if(b)b.textContent='🎧 Read aloud';return;}
    var q=[];(S.verses||[]).forEach(function(l,i){l.forEach(function(v,j){q.push({i:i,j:j,t:v.t});});});
    if(!q.length)return;
    speechSynthesis.cancel();speaking=true;if(b)b.textContent='⏹ Stop';
    var idx=0;
    function next(){
      if(!speaking)return;
      if(idx>=q.length){speaking=false;clearMarks();if(b)b.textContent='🎧 Read aloud';listened();return;}
      var item=q[idx++],el=document.getElementById('bsV'+item.i+'_'+item.j);
      var words=el?el.querySelectorAll('.w'):[],starts=[],pos=0;
      item.t.split(/\s+/).forEach(function(w){var k=item.t.indexOf(w,pos);starts.push(k);pos=k+w.length;});
      var u=new SpeechSynthesisUtterance(item.t);u.rate=0.95;
      u.onstart=function(){clearMarks();if(el){el.classList.add('reading');try{el.scrollIntoView({behavior:'smooth',block:'center'});}catch(e){}}};
      u.onboundary=function(ev){if(ev.name&&ev.name!=='word')return;var c=ev.charIndex,w=0;for(var k=0;k<starts.length;k++){if(starts[k]<=c)w=k;else break;}
        var on=el&&el.querySelector('.w.on');if(on)on.classList.remove('on');if(words[w])words[w].classList.add('on');};
      u.onend=function(){next();};u.onerror=function(){next();};
      speechSynthesis.speak(u);
    }
    next();
  }
  function clearMarks(){document.querySelectorAll('#bsBody .reading,#bsBody .w.on').forEach(function(e){e.classList.remove('reading');e.classList.remove('on');});}
  function listened(){tick('spBibleAudio');if(S){S.done.audio=true;S.done[1]=true;persist();if(S.step===1)render();}}
  // Listened to the audiobook: no need to read it again here — on to the Study step.
  function listenedApp(){listened();if(S&&S.step===1){toast('🎧 Bible Audio ✓ — on to the study');stepTo(2);}}
  function xref(i){
    var el=document.getElementById('bsX'+i);if(!el)return;if(el.innerHTML){el.innerHTML='';return;}
    var r=parseSeg(String(S.study.crossrefs[i]).split(/\s+[—–-]\s+/)[0]);if(!r){el.textContent='(couldn\'t read that reference)';return;}
    el.textContent='⏳';fetchSeg(r).then(function(vs){el.innerHTML=vs.map(function(v){return '<sup>'+v.v+'</sup> '+esc(v.t);}).join(' ')||'(not found)';}).catch(function(){el.textContent='(couldn\'t load)';});
  }
  function close(){try{speechSynthesis.cancel();}catch(e){}speaking=false;var ov=document.getElementById('bstudyOverlay');if(ov)ov.remove();document.body.style.overflow='';renderCard();}

  // ── Card controls ──
  function pickDay(){
    var cur=(ls('bstudy_day')||{}).date===todayKey()?(ls('bstudy_day')||{}).day:mmdd(todayKey());
    var v=prompt('Which One Year Chronological day are you on? (month and day, e.g. 11/16)\nUse this if you\'re behind or ahead. Leave as is for today\'s date.',cur.replace('-','/'));
    if(v===null)return;var m=v.trim().match(/^(\d{1,2})[\/-](\d{1,2})$/);if(!m){alert('Use month/day, like 11/16.');return;}
    var md=String(+m[1]).padStart(2,'0')+'-'+String(+m[2]).padStart(2,'0');if(!OYB[md]){alert('That day isn\'t in the plan.');return;}
    ls('bstudy_day',{date:todayKey(),day:md});
    var s=ls(sessionKey());if(s&&!s.finished&&s.day!==md)ls(sessionKey(),null);   // an unfinished study for another day is replaced
    renderCard();toast('📖 Reading for '+new Date('2026-'+md+'T12:00').toLocaleDateString('en-US',{month:'long',day:'numeric'}));
  }
  function fixReading(){
    var md=(ls('bstudy_day')||{}).date===todayKey()?(ls('bstudy_day')||{}).day:mmdd(todayKey());
    var v=prompt('Today\'s reading in your One Year Chronological Bible ('+md.replace('-','/')+'). Separate passages with ";" — e.g. Acts 17:16-18:3; 1 Thessalonians 1:1-5:11\n\nClear it to go back to the built-in list.',oybReading(md));
    if(v===null)return;var f=oybFixes();if(v.trim()&&v.trim()!==OYB[md])f[md]=v.trim();else delete f[md];ls('bstudy_oyb_fix',f);
    var s=ls(sessionKey());if(s&&!s.finished)ls(sessionKey(),null);renderCard();
  }
  function choosePlan(){
    var p=plan();
    var c=prompt('Study plan:\n 1 = One Year Chronological Bible (follows each day\'s reading)\n 2 = One book, chapter by chapter\n 3 = Your Daily Bread verse\n\nCurrent: '+(p.type==='oneyear'?'1':p.type==='book'?'2 ('+p.book+' '+p.chapter+')':'3'),p.type==='oneyear'?'1':p.type==='book'?'2':'3');
    if(c===null)return;c=c.trim();
    if(c==='1')ls('bstudy_plan',{type:'oneyear'});
    else if(c==='3')ls('bstudy_plan',{type:'verse'});
    else if(c==='2'){var b=prompt('Which book? (e.g. John, Romans, Psalms)',p.book||'John');if(!b)return;var name=bookName(b);if(!chapters(name)){alert('I couldn\'t find that book.');return;}
      var ch=prompt('Start at chapter (1–'+chapters(name)+')','1');ls('bstudy_plan',{type:'book',book:name,chapter:Math.max(1,Math.min(chapters(name),parseInt(ch,10)||1))});}
    else return;
    var s=ls(sessionKey());if(s&&!s.finished)ls(sessionKey(),null);renderCard();
  }

  // ── Journal ──
  function journal(){
    var keys=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(/^bstudy_\d{4}-\d{2}-\d{2}$/.test(k))keys.push(k);}}catch(e){}
    keys.sort().reverse();var items=keys.map(function(k){return ls(k);}).filter(function(o){return o&&o.study;});
    var ov=document.createElement('div');ov.id='bstudyOverlay';
    ov.innerHTML='<div class="bs-sheet"><div class="bs-top"><button class="bs-x" onclick="bibleStudy.close()">✕</button><div class="bs-title">🗂️ Study Journal</div><div class="bs-ref">'+items.length+' studies</div></div>'
      +'<div style="padding:10px 16px 0"><input id="bsSearch" placeholder="🔍 Search your studies…" style="width:100%;box-sizing:border-box;padding:9px 12px;border:1px solid #c4b5fd;border-radius:10px;font-size:.9rem"></div><div class="bs-body" id="bsJ"></div></div>';
    document.body.appendChild(ov);document.body.style.overflow='hidden';
    function ref(o){return o.segs?o.segs.map(segLabel).join('; '):(o.book+' '+o.chapter);}
    function draw(q){q=String(q||'').toLowerCase();
      document.getElementById('bsJ').innerHTML=items.filter(function(o){return !q||JSON.stringify(o).toLowerCase().indexOf(q)>=0;}).map(function(o){var a=o.answers||{};
        return '<details class="bs-card"><summary><b>'+esc(new Date(o.date+'T12:00').toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',year:'numeric'}))+'</b> · '+esc((o.study||{}).focus_label||ref(o))+' <span class="bs-note">'+esc((o.study||{}).theme||'')+'</span></summary>'
          +'<div class="bs-note">'+esc(ref(o))+'</div>'
          +(a.s?'<div class="bs-h2">S</div><div class="bs-text">'+esc(a.s)+'</div>':'')
          +((a.o||[]).filter(Boolean).length?'<div class="bs-h2">O</div><div class="bs-text">'+(a.o||[]).filter(Boolean).map(esc).join('<br>')+'</div>':'')
          +((a.a||[]).filter(Boolean).length?'<div class="bs-h2">A</div><div class="bs-text">'+(a.a||[]).filter(Boolean).map(esc).join('<br>')+'</div>':'')
          +(a.p?'<div class="bs-h2">P</div><div class="bs-text">'+esc(a.p)+'</div>':'')
          +'<div class="bs-row"><button class="bs-btn2" onclick="bibleStudy.close();bibleStudy.open(\''+o.date+'\')">Open study</button></div></details>';}).join('')||'<div class="bs-load">No studies yet.</div>';}
    draw('');document.getElementById('bsSearch').addEventListener('input',function(e){draw(e.target.value);});
  }

  // ── Daily AI Review input: today's study (passage, theme, key verse, SOAP answers, steps done) ──
  window.bibleStudyDayText=function(date){
    try{var o=ls('bstudy_'+date);if(!o)return '';var st=o.study||{},a=o.answers||{},out=[];
      out.push('Reading: '+((o.segs||[]).map(segLabel).join('; ')||o.label||''));
      if(st.focus_label)out.push('Study focus: '+st.focus_label);
      if(st.theme)out.push('Theme: '+st.theme);
      if(st.key_verse)out.push('Key verse: '+(st.key_verse.ref||st.key_verse.text||st.key_verse));
      ['s','o','a','p'].forEach(function(k){var v=a[k];if(Array.isArray(v))v=v.filter(Boolean).join(' / ');if(v)out.push({s:'Scripture noted',o:'Observation',a:'Application',p:'Prayer'}[k]+': '+v);});
      out.push(o.finished?'Status: study finished':'Status: started, not finished');
      return out.join('\n');}catch(e){return '';}
  };
  // ── Weekly AI Review input ──
  window.bibleStudyWeekText=function(from,to){
    var out=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var m=k&&k.match(/^bstudy_(\d{4}-\d{2}-\d{2})$/);if(m&&m[1]>=from&&m[1]<=to){var o=ls(k);if(o&&o.study){var a=o.answers||{};
      out.push(m[1]+': '+(o.study.focus_label||(o.segs||[]).map(segLabel).join('; '))+' ('+(o.study.theme||'')+')'+(a.a&&a.a.filter(Boolean).length?' — application: '+a.a.filter(Boolean).join(' / '):''));}}}}catch(e){}
    var mem=ls('bstudy_mem_'+from);
    return out.length?'\n\nBIBLE STUDY THIS WEEK (One Year Chronological Bible)\n'+out.sort().join('\n')+(mem?'\nMemory verse: '+mem.ref:''):'';
  };

  // ── The card on the Bible tab ──
  function renderCard(){
    var host=document.getElementById('bibleStudyCard');
    if(!host){var tab=document.querySelector('#tab-bible > div');if(!tab)return;host=document.createElement('div');host.id='bibleStudyCard';tab.insertBefore(host,tab.firstChild);}
    var s=ls(sessionKey()),p=plan(),mem=memoryLine();
    var dayPick=(ls('bstudy_day')||{}).date===todayKey()?(ls('bstudy_day')||{}).day:'';
    var r=s&&s.segs?{label:s.label,text:s.segs.map(segLabel).join('; ')}:readingFor(todayKey(),dayPick);
    var status=s&&s.finished?'✓ Done today':s&&s.study?'In progress':'Today';
    host.innerHTML='<div class="bs-hero"><div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><div style="flex:1;min-width:180px"><div class="bs-hero-t">📖 Daily Bible Study</div>'
      +'<div class="bs-hero-s"><b>'+status+'</b> · '+esc(r.label||'')+'</div><div class="bs-hero-s" style="margin-top:2px">'+esc(parseSegs(r.text||'').map(segLabel).join('; ')||r.text||'')+'</div></div>'
      +'<button class="bs-btn" onclick="bibleStudy.open()">'+(s&&s.finished?'Review study':s&&s.study?'Continue ›':'Start study ›')+'</button></div>'
      +'<div class="bs-hero-s" style="margin-top:6px">Prayer → Read & listen → Study (SOAP) → Worship → Prayer. Ticks your four checks below as you go.</div>'
      +(mem?'<div class="bs-mem">🧠 Memory verse this week: '+esc(mem)+'</div>':'')
      +'<div class="bs-row" style="margin-top:8px">'
      +(p.type==='oneyear'?'<button class="bs-btn2" onclick="bibleStudy.day()">📅 Day: '+esc(new Date('2026-'+(dayPick||mmdd(todayKey()))+'T12:00').toLocaleDateString('en-US',{month:'short',day:'numeric'}))+'</button><button class="bs-btn2" onclick="bibleStudy.fix()">✏️ Fix reading</button>':'')
      +'<button class="bs-btn2" onclick="bibleStudy.plan()">📚 '+(p.type==='oneyear'?'Plan':p.type==='book'?esc(p.book)+(p.done?' — finished':' · ch. '+p.chapter):'Daily Bread verse')+'</button><button class="bs-btn2" onclick="bibleStudy.journal()">🗂️ Journal</button></div></div>';
  }

  var css='#bibleStudyCard .bs-hero{background:linear-gradient(135deg,#4c1d95,#7c3aed);color:#fff;border-radius:14px;padding:14px 16px;margin-bottom:14px;box-shadow:0 4px 14px rgba(76,29,149,.25)}'
    +'.bs-hero-t{font-weight:800;font-size:1.05rem}.bs-hero-s{font-size:.8rem;opacity:.9}.bs-mem{margin-top:8px;background:rgba(255,255,255,.14);border-radius:9px;padding:7px 10px;font-size:.82rem;line-height:1.45}'
    +'#bstudyOverlay{position:fixed;inset:0;background:rgba(15,10,30,.55);z-index:2147483600;display:flex;justify-content:center;align-items:stretch}'
    +'#bstudyOverlay .bs-sheet{background:#faf8ff;width:100%;max-width:720px;display:flex;flex-direction:column;height:100%}'
    +'.bs-top{display:flex;align-items:center;gap:10px;padding:12px 16px;background:#4c1d95;color:#fff}.bs-title{font-weight:800}.bs-ref{margin-left:auto;font-size:.8rem;opacity:.9;text-align:right}'
    +'.bs-x{background:rgba(255,255,255,.15);border:none;color:#fff;border-radius:999px;width:32px;height:32px;font-size:1rem;cursor:pointer}'
    +'.bs-steps{display:flex;gap:4px;overflow-x:auto;padding:8px 12px;background:#ede9fe}.bs-steps span{white-space:nowrap;font-size:.75rem;padding:5px 9px;border-radius:999px;color:#6d28d9;cursor:pointer}'
    +'.bs-steps span.on{background:#7c3aed;color:#fff;font-weight:700}.bs-steps span.ok{color:#16a34a;font-weight:600}'
    +'.bs-body{flex:1;overflow-y:auto;padding:14px 16px}.bs-nav{display:flex;justify-content:space-between;padding:10px 16px;border-top:1px solid #e9e5f5;background:#fff}'
    +'.bs-btn,.bs-btn2{border:none;border-radius:999px;padding:9px 16px;font-weight:700;font-size:.86rem;cursor:pointer;text-decoration:none;display:inline-block}'
    +'.bs-btn{background:#7c3aed;color:#fff}#bibleStudyCard .bs-btn{background:#fff;color:#4c1d95}.bs-btn2{background:#ede9fe;color:#6d28d9}#bibleStudyCard .bs-btn2{background:rgba(255,255,255,.16);color:#fff}'
    +'.bs-card{background:#fff;border:1px solid #e9e5f5;border-radius:12px;padding:12px 14px;margin-bottom:12px}.bs-card summary{cursor:pointer}'
    +'.bs-pray{background:#f5f3ff;border-color:#ddd6fe}.bs-h{font-size:.72rem;font-weight:800;color:#7c3aed;text-transform:uppercase;letter-spacing:.04em;margin-bottom:6px}'
    +'.bs-h2{font-weight:800;color:#4c1d95;margin:14px 0 4px}.bs-text{font-size:.92rem;line-height:1.6;color:#2e1065;white-space:pre-wrap}.bs-note{font-size:.78rem;color:#6b7280;margin:8px 0}'
    +'.bs-q{font-size:.86rem;color:#374151;margin:6px 0 4px}.bs-body textarea{width:100%;box-sizing:border-box;border:1px solid #ddd6fe;border-radius:9px;padding:8px 10px;font-size:.9rem;font-family:inherit;background:#fff}'
    +'.bs-scripture{font-family:Georgia,serif;font-size:1.02rem;line-height:1.75;color:#1f1147}.bs-scripture sup{color:#a78bfa;font-size:.65em;margin-right:2px}.bs-scripture .kv{background:#fef3c7;border-radius:4px}'
    +'.bs-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:8px 0}.bs-load{padding:30px 10px;text-align:center;color:#6d28d9}.bs-link{background:none;border:none;color:#7c3aed;font-weight:700;cursor:pointer;padding:4px 0}'
    +'.bs-scripture .fo{background:#ede9fe;border-radius:4px}.bs-ch{display:inline-block;color:#7c3aed;font-family:system-ui;margin:0 4px 0 2px}'
    +'.bs-v.reading{background:#e0f2fe;border-radius:4px}.w.on{background:#7c3aed;color:#fff;border-radius:3px;padding:0 1px}'
    +'.bs-xref{color:#6d28d9;font-weight:600;cursor:pointer;padding:5px 0}.bs-xtext{font-family:Georgia,serif;font-size:.9rem;color:#374151;line-height:1.6}';
  var st=document.createElement('style');st.textContent=css;document.head.appendChild(st);

  // For BeyondStride's Life tab: today's study at a glance (same facts as the Bible Study card).
  function info(){
    var sv=ls(sessionKey()),dayPick=(ls('bstudy_day')||{}).date===todayKey()?(ls('bstudy_day')||{}).day:'';
    var r=sv&&sv.segs?{label:sv.label,text:sv.segs.map(segLabel).join('; ')}:readingFor(todayKey(),dayPick);
    var steps=['Open','Read','Study','Worship','Close'],done=sv&&sv.done?Object.keys(sv.done).filter(function(k){return sv.done[k];}).length:0;
    return {status:sv&&sv.finished?'done':sv&&sv.study?'progress':'new',label:r.label||'',passages:parseSegs(r.text||'').map(segLabel).join('; ')||r.text||'',
      memory:memoryLine(),step:sv&&typeof sv.step==='number'?steps[sv.step]||'':'',hasKey:!!journalAIKey(),
      planType:plan().type,day:new Date('2026-'+(dayPick||mmdd(todayKey()))+'T12:00').toLocaleDateString('en-US',{month:'short',day:'numeric'}),
      planLabel:(function(p){return p.type==='oneyear'?'Plan':p.type==='book'?p.book+(p.done?' — finished':' · ch. '+p.chapter):'Daily Bread verse';})(plan())};
  }
  // ── For BeyondStride's native Bible Study screen: the same session, driven without this page's overlay. ──
  // Same prompts, same saved session (bstudy_<date>), same checks ticked; the phone draws the screens.
  // Days with a saved study (for ‹ › on the app's Bible Study screen), oldest first.
  function nDates(){var out=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(!/^bstudy_\d{4}-\d{2}-\d{2}$/.test(k))continue;var o=ls(k);if(o&&o.study)out.push(k.slice(7));}}catch(e){}return JSON.stringify(out.sort());}
  function nStart(date){
    if(!journalAIKey())return 'nokey';
    date=date||todayKey();var saved=ls(sessionKey(date));   // a past date reopens that day's saved study (from the Journal)
    if(!S||S.date!==date){
      if(saved&&saved.segs)S=saved;
      else{var dp=(ls('bstudy_day')||{}).date===date?(ls('bstudy_day')||{}).day:'';var r=readingFor(date,dp);
        S={date:date,source:r.source,day:r.day||'',text:r.text,label:r.label,segs:parseSegs(r.text),key:r.key||null,focus:null,study:null,answers:{s:'',o:[],a:[],p:''},step:0,done:{},finished:false};}
    }
    if(!S.segs||!S.segs.length){S=null;return 'noreading';}
    S._err='';if(!S.study)prepare();
    return 'ok';
  }
  // 🖍 Your highlights, by verse ("Book C:V" → [[from,to,color],…], character positions in the verse text). Kept with the
  // rest of the Life data (so they're in BeyondStride's Backup).
  function hlAll(){return ls('bstudy_highlights')||{};}
  function hlFor(lists){if(!lists)return {};var all=hlAll(),out={};lists.forEach(function(l){l.forEach(function(x){var k=x.b+' '+x.c+':'+x.v;if(all[k])out[k]=all[k];});});return out;}
  function nHl(key,json){var all=hlAll(),v=[];try{v=JSON.parse(json)||[];}catch(e){}if(v.length)all[key]=v;else delete all[key];ls('bstudy_highlights',all);return 'ok';}
  function nState(){
    if(!S)return JSON.stringify({none:true});
    if(S.study&&S.step===1&&!S.verses)loadVerses();
    if(S.study&&S.step===4&&!S.closing)closing();
    var st=S.study,v=null;
    if(S.verses)v=S.verses.map(function(list,i){return {ref:segLabel(S.segs[i]),nlt:nltLink([S.segs[i]]),verses:list.map(function(x){return {b:x.b,c:x.c,v:x.v,t:x.t,k:isKey(x)?1:inFocus(x)?2:0};})};});
    return JSON.stringify({date:S.date,label:S.label,ref:refText(),step:S.step,done:S.done,finished:!!S.finished,study:st||null,answers:S.answers,closing:S.closing||'',
      preparing:!!S._preparing,error:S._err||'',verses:v,loadingVerses:!!S._loading,nlt:nltLink(S.segs),memory:memoryLine(),xref:S._x||{},hl:hlFor(S.verses)});
  }
  function nAnswer(k,val){if(!S)return false;if(k==='s'||k==='p')S.answers[k]=String(val||'');else{var arr=S.answers[k[0]];if(!arr)return false;arr[+k.slice(1)]=String(val||'');}persist();return true;}
  function nXref(i){
    if(!S||!S.study)return false;S._x=S._x||{};if(S._x[i]){delete S._x[i];return true;}
    var r=parseSeg(String(S.study.crossrefs[i]).split(/\s+[—–-]\s+/)[0]);if(!r){S._x[i]='(couldn\'t read that reference)';return true;}
    S._x[i]='⏳';fetchSeg(r).then(function(vs){S._x[i]=vs.map(function(v){return v.v+' '+v.t;}).join(' ')||'(not found)';}).catch(function(){S._x[i]='(couldn\'t load)';});return true;
  }

  // ── For BeyondStride's native screens: the card controls without prompt() boxes (same saved keys, same rules). ──
  function curDay(){return (ls('bstudy_day')||{}).date===todayKey()?(ls('bstudy_day')||{}).day:mmdd(todayKey());}
  function nDay(v){var m=String(v||'').trim().match(/^(\d{1,2})[\/-](\d{1,2})$/);if(!m)return 'Use month/day, like 11/16.';
    var md=String(+m[1]).padStart(2,'0')+'-'+String(+m[2]).padStart(2,'0');if(!OYB[md])return 'That day isn\'t in the plan.';
    ls('bstudy_day',{date:todayKey(),day:md});var s=ls(sessionKey());if(s&&!s.finished&&s.day!==md)ls(sessionKey(),null);renderCard();return 'ok';}
  function nFix(v){var md=curDay(),f=oybFixes();v=String(v||'').trim();if(v&&v!==OYB[md])f[md]=v;else delete f[md];ls('bstudy_oyb_fix',f);
    var s=ls(sessionKey());if(s&&!s.finished)ls(sessionKey(),null);renderCard();return 'ok';}
  function nPlan(type,book,ch){
    if(type==='oneyear')ls('bstudy_plan',{type:'oneyear'});else if(type==='verse')ls('bstudy_plan',{type:'verse'});
    else if(type==='book'){var name=bookName(book);if(!chapters(name))return 'I couldn\'t find that book.';ls('bstudy_plan',{type:'book',book:name,chapter:Math.max(1,Math.min(chapters(name),parseInt(ch,10)||1))});}
    else return 'Pick a plan.';
    var s=ls(sessionKey());if(s&&!s.finished)ls(sessionKey(),null);renderCard();return 'ok';}
  function nCard(){var md=curDay(),p=plan();return {day:md.replace('-','/'),reading:oybReading(md),builtin:OYB[md]||'',fixed:!!oybFixes()[md],plan:p,
    books:BOOKS.map(function(b){return [b[0],chapters(b[0])||0];})};}
  function nJournal(){var keys=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(/^bstudy_\d{4}-\d{2}-\d{2}$/.test(k))keys.push(k);}}catch(e){}
    keys.sort().reverse();return keys.map(function(k){return ls(k);}).filter(function(o){return o&&o.study;}).map(function(o){var a=o.answers||{},st=o.study||{};
      return {date:o.date,label:st.focus_label||'',theme:st.theme||'',ref:o.segs?o.segs.map(segLabel).join('; '):((o.book||'')+' '+(o.chapter||'')),
        s:a.s||'',o:(a.o||[]).filter(Boolean),a:(a.a||[]).filter(Boolean),p:a.p||'',finished:!!o.finished};});}
  window.bibleStudy={nDates:nDates,nHl:nHl,nDay:nDay,nFix:nFix,nPlan:nPlan,nCard:nCard,nJournal:nJournal,info:info,nStart:nStart,nState:nState,nStep:function(i){stepTo(i);return nState();},nAnswer:nAnswer,nXref:nXref,
    nListened:function(){listened();return true;},nListenedApp:function(){listened();if(S&&S.step===1)stepTo(2);return true;},nRetry:function(){if(S){S._err='';S._preparing=false;if(!S.study)prepare();}return true;},open:open,close:close,step:function(d){stepTo(S.step+d);},go:function(i){stepTo(i);},listen:listen,listened:listened,listenedApp:listenedApp,xref:xref,plan:choosePlan,journal:journal,day:pickDay,fix:fixReading,
    retry:function(){S._preparing=false;render();}};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){setTimeout(renderCard,800);});else setTimeout(renderCard,800);
})();
