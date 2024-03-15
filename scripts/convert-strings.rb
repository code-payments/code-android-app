#!/usr/bin/ruby

# based on https://github.com/tmurakam/cashflow/blob/0a01ac9e0350dfb04979986444244f8daf4cb5a8/android/convertStrings.rb
# support comments and Converter such as "%@", "%d", "%0.1f"...
# in your directory : ./main.rb Localizable.strings

file = File.open("strings-localized.xml", "w");

file.puts "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
file.puts "<resources>"

multiple_line_comment = false

ARGF.each do |line|
    if (line =~ /\"(.*)\"\s*=\s*\"(.*)\"/)
        name = $1
        value = $2

        # Skip if the value is empty
        next if value.empty?

        name.gsub!(/[ .]/, "_")

        value.gsub!(/&/, "&amp;")
        value.gsub!(/</, "&lt;")
        value.gsub!(/'/, "\\\\'")

        i = 0
        # convert %@ to %1$s
        value.gsub!(/%([0-9.]*[@sScCdoxXfeEgabBhH])/) {|s|
        	i += 1
        	match = $1
        	match.gsub!(/@/, "s")
        	"%#{i}$#{match}"
        }

        file.puts "  <string name=\"#{name}\">#{value}</string>"
    # one line comment // The cake is a lie
    # multiple line comment on one line /* The cake is a lie */
    elsif (line =~ /\/\/(.*)/ || line =~ /\/\*(.*)\*\//)
        file.puts "<!--#{$1}-->"
    # multiple line comment (start)
    elsif (line =~ /\/\*(.*)/)
        file.puts "<!--#{$1}"
        multiple_line_comment = true
    # multiple line comment (middle or end)
    elsif (multiple_line_comment)
        #end of the multiple line comment
        if (line =~ /(.*)\*\//)
            file.puts "#{$1}-->"
            multiple_line_comment = false
        else
            file.puts line
        end
    elsif (line =~ /\n/)
        file.puts line
    end
end

file.puts "</resources>"