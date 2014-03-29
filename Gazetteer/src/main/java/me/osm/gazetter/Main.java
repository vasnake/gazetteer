package me.osm.gazetter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.out.CSVOutConvertor;
import me.osm.gazetter.out.OutWriter;
import me.osm.gazetter.split.Split;
import me.osm.gazetter.striper.Slicer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

/**
 * Entry point for executable jar.
 * */
public class Main {
	
	
	private static final String JOIN_COMMON_VAL = "common";
	private static final String JOIN_COMMON_OPT = "--common";

	private static final String DATA_DIR_VAL = "data_dir";
	private static final String DATA_DIR_OPT = "--data-dir";
	
	private static final String LOG_OPT = "--log-level";
	private static final String LOG_VAL = "log_level";

	private static final String COMMAND = "command";

	public static interface CommandDescription {
		public String longName(); 
		public String help(); 
	}
	
	private enum Command implements CommandDescription {
	    SPLIT {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Prepare osm data. Split nodes, ways and relations.";}
	    }, 
	    SLICE {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Parse features from osm data and write it into stripes 0.1 degree wide.";}
	    }, 
	    JOIN {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Join features. Made spatial joins for address points inside polygons and so on.";}
	    }, 
	    OUT_CSV {
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	public String help() {return "Write data out in csv format.";}
	    };

	};

	/**
	 * Parse arguments and run tasks accordingly.
	 * */
	public static void main(String[] args) {
		
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(org.slf4j.impl.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH.mm.ss.S");
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");
		
		ArgumentParser parser = getArgumentsParser();
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, (String)namespace.get(LOG_VAL));

			if(namespace.get(COMMAND).equals(Command.SPLIT)) {
				Split splitter = new Split(new File(namespace.getString(DATA_DIR_VAL)), namespace.getString("osm_file"));
				splitter.run();
				System.exit(0);
			}

			if(namespace.get(COMMAND).equals(Command.SLICE)) {
				List<String> types = new ArrayList<String>();
				if(namespace.get("feature_types") instanceof String) {
					types.add((String)namespace.get("feature_types"));
				}
				else if (namespace.get("feature_types") instanceof Collection) {
					types.addAll((Collection)namespace.get("feature_types"));
				}
				Slicer.run(namespace.getString(DATA_DIR_VAL), namespace.getString("poi_catalog"), types);
				System.exit(0);
			}

			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				Options.initialize(AddrLevelsSorting.valueOf(namespace.getString("addr_order")));
				
				Joiner.run(namespace.getString(DATA_DIR_VAL), namespace.getString(JOIN_COMMON_VAL));
				
				System.exit(0);
			}
			
			if(namespace.get(COMMAND).equals(Command.OUT_CSV)) {
				new OutWriter(namespace.getString(DATA_DIR_VAL), new CSVOutConvertor()).write();
				System.exit(0);
			}
			
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		
	}

	/**
	 * Generate arguments parser.
	 * */
	private static ArgumentParser getArgumentsParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("gazetter")
                .defaultHelp(true)
                .description("Create alphabetical index of osm file features");

        parser.addArgument(DATA_DIR_OPT).required(false).
                help("Use folder as data storage.").setDefault("slices");
        
        parser.addArgument(LOG_OPT).required(false).setDefault("WARN");
        
        Subparsers subparsers = parser.addSubparsers();
		
        //split
        {
        	Command command = Command.SPLIT;
			Subparser split = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
        	
        	split.addArgument("osm_file").required(true)
        		.help("Path to osm file. *.osm *.osm.bz *.osm.gz supported.");
        }
        
		//slice
		{
			Command command = Command.SLICE;
			Subparser slice = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			slice.addArgument("--poi-catalog").help("Path to osm-doc catalog.").setDefault("jar");
			
			slice.addArgument("feature_types").help("Parse and slice axact feature(s) type.")
				.choices(Slicer.sliceTypes).nargs("*").setDefault("all").setConst("all");
			
		}

		//join
		{
			Command command = Command.JOIN;
			Subparser join = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			join.addArgument(JOIN_COMMON_OPT)
				.help("Path for *.json with array of features which will be added to boundaries "
						+ "list for every feature.");
			
			join.addArgument("--addr-order").choices("HN_STREET_CITY", "STREET_HN_CITY", "CITY_STREET_HN").setDefault("HN_STREET_CITY")
				.help("How to sort addr levels in full addr text");

			join.addArgument("--addr-formatter")
				.help("Path to *.js or *.groovy file with full addresses texts formatter.");
			
		}

		//out
		{
			Command command = Command.OUT_CSV;
			subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
		}
		
		return parser;
	}

}
