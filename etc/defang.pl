#!perl

#
# Takes the Tiger out of your tank. Accepts a JSR-166 main source directory and
# a target root directory for the result, and processes files in several packages
# to corresponding files in the target hierarchy, renaming java.* packages to
# jsr166.* packages.
#
# Makes use of custom sources in emulation directory tree
#
# Processing does the following:
# - renames packages
# - renames imports
# - removes generics
# - adds casts and changes types to cope with loss of generics
# - replaces calls to System.nanoTime()
#

use Getopt::Std;
use File::Copy;
use File::Find;
use File::Path;
use FindBin;

my %args;

getopts("e:s:t:v", \%args) or die <<USAGE;

Usage:

    defang.pl [-v] [-s sourcedir] [-t targetdir] [-e emulationdir]

USAGE

my $verbose = $args{v};
(my $srcdir  = $args{s} || "$FindBin::Bin/../src/main")         =~ s#\\#/#g;
(my $tgtdir  = $args{t} || "$FindBin::Bin/../src/pretiger")     =~ s#\\#/#g;
(my $emudir  = $args{e} || "$FindBin::Bin/pretiger")            =~ s#\\#/#g;

print "source is $srcdir\n" if $verbose;
print "target is $tgtdir\n" if $verbose;
print "emulation is $emudir\n" if $verbose;

my @pkgs = qw(
    java/util
    java/util/concurrent
    java/util/concurrent/atomic
    java/util/concurrent/locks
);
my %pkgs;
$pkgs{$_}++ for @pkgs;

print "packages:\n\t" . join("\n\t", sort keys %pkgs) . "\n" if $verbose;

find(\&processSource, $srcdir);
find(\&copyEmulation, $emudir);

sub copyEmulation {
    -d and /CVS/ and $File::Find::prune = 1, return;

    (my $old = $File::Find::name) =~ s#^$emudir##; $old =~ s#^/##;
    (my $new = $File::Find::name) =~ s#^$emudir#$tgtdir#;
    if (-d) {
        mkpath $new;
        print "mkdir $old\n" if $verbose;
    }
    else {
        copy $File::Find::name, $new;
        print "copied $old\n" if $verbose;
    }
}

sub processSource {
    -d and /CVS/ and $File::Find::prune = 1, return;

    (my $dir = $File::Find::dir ) =~ s#^$srcdir##; $dir =~ s#^/##;
    (my $old = $File::Find::name) =~ s#^$srcdir##; $old =~ s#^/##;
    (my $new = $File::Find::name) =~ s#^$srcdir/java#$tgtdir/jsr166#;

    if (-d) {
        if ($pkgs{$old}) {
            mkpath $new;
            print "mkdir $old\n" if $verbose;
        }
    }
    else {
        if ($pkgs{$dir} && !exclude($old)) {
            process($File::Find::name, $new);
            print "processed $old\n" if $verbose;
        }
    }
}

sub exclude {
    local $_ = shift;

    /Random.java$/                   ||
    /LinkedList.java$/               ||
    /AtomicMarkableReference.java$/  ||
    /AtomicStampedReference.java$/   ||
    0;
}

my $indent = 0;

sub process {
    my ($from, $to) = @_;
    if ($from !~ /.java$/) {
        copy $from, $to;
        return;
    }

    open S, "<", $from or die "can't open $from for reading\n";
    open T, ">", $to   or die "can't open $to for writing\n";

    while (<S>) {
        # rename package
        s/^package java.util;/package jsr166.util;\n\nimport java.util.*;/;
        s/^package java.(.*);/package jsr166.$1;/;

        # deal with imports
        s/^import java.util.(\w*Queue);/import jsr166.util.$1;/;
        s/^import java.util.concurrent/import jsr166.util.concurrent/;
        s/^(import java.util.\*;)/$1\nimport jsr166.util.*;/;
        s/^import sun.misc.(?:Unsafe|\*);/import jsr166.misc.Unsafe;/;

        # remove generics
        s/\s?<([A-Z?][^<>]*)>//g; # remove inner type parameters
        s/\s?<([A-Z?][^<>]*)>//g; # remove outer type parameters
        s/\([A-Z]\[]\)\s?//g;     # remove array casts
        s/\([A-Z]\)\s?//g;        # remove Object casts
        s/\b[A-Z]\b/Object/g;     # replace generic types with Object


        # convert use of locks to synchronized blocks where possible
        # (not in LBQ or in comments), ignoring use of newCondition
        if ($from !~ /LinkedBlockingQueue/ && !m/^\s*\*\s+/) {
            if ($indent) {
                s/^/    /;
            }
            if (s/\b(\w+)\.lock\(\);/synchronized ($1) {/) {
                ++$indent;
            }
            if (s/\b(\w+)\.lockInterruptibly\(\);/if (Thread.interrupted()) throw new InterruptedException(); synchronized ($1) {/) {
                ++$indent;
            }
            if (s/if \((\w+)\.tryLock\(\)\)/synchronized ($1) {/) {
                ++$indent;
            }
            if (s/(\s{4}\b\w+)\.unlock\(\);/} \/\/ vacuous try-finally/g) {
                --$indent;
            }
        }

        # casts and generic arguments for various methods
        s/\b(workQueue\.(?:poll|peek|take|remove|element))/(Runnable)$1/g;
        s/public Runnable (poll|peek|take|remove|element|next)/public Object $1/g;
        s/(put|add|offer)\(Runnable/$1(Object/g;

        # only use of newCondition in source is with RL.ConditionObject.
        s/\b((?:this|\w*[Ll]ock)\.newCondition\(\))/((ReentrantLock.ConditionObject)$1)/g;
        s/\b([A-Z]\w+)\s+newCondition\(\)/Condition newCondition()/g;

        # cast it.next() to Worker in TPE
        if ($from =~ /ThreadPoolExecutor/) {
            s/(it.next\(\))/((Worker)$1)/g;
        }

        # cast to Entry in CHM
        if ($from =~ /ConcurrentHashMap/) {
            s/\bMap.Entry next\(\)/Object next()/g;
            s/\b([a-z]+).next\(\)/((Entry)$1.next())/g;
        }

        # cast to Delayed/Comparable in DQ
        if ($from =~ /DelayQueue/) {
            s/Object first/Delayed first/g;
            s/\bq\.peek\(\)/((Delayed)q.peek())/g;
            s/\b(\w+)(\.compareTo)/((Comparable)$1)$2/g;
        }

        # cast Array.newInstance result to Object[]
        s/([\w.]+Array\.newInstance)/(Object[])$1/g;


        # replace calls to System.nanoTime()
        s/System.nanoTime\(\)/(1000*1000*System.currentTimeMillis())/g;


        print T;

    }
    close T;
    close S;
}


__END__
