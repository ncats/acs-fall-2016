#!/bin/perl -w

use strict;

my %cids;
foreach my $file (@ARGV) {
    print "processing $file...";
    open (F, $file) or die "eh, where's the love?";
    my $cnt = 0;
    my @name = split /\//, $file;
    my $name = $name[$#name];
    while (<F>) {
	chomp;
	my $c = $cids{$_};
	if (defined $c) {
	    $c->{$name} = undef;
	}
	else {
	    $cids{$_} = {$name => undef};
	}
	++$cnt;
    }
    close F;
    print "$cnt\n";
}

print "generating histogram...\n";
open (F, ">promiscous.txt") or die "oh, c'mon man!";
foreach my $key (sort {scalar (keys %{$cids{$b}}) <=>
			   scalar (keys %{$cids{$a}})
			   || $a <=> $b
		 } keys %cids) {
    my @vals = keys %{$cids{$key}};
    print F "$key ", scalar @vals, " ", scalar(@vals)/scalar(@ARGV);
    foreach my $val (@vals) {
	print F " $val";
    }
    print F "\n";
}
close F;

