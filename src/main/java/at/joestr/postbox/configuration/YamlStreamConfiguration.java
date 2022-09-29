//
// MIT License
//
// Copyright (c) 2022 Joel Strasser
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package at.joestr.postbox.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Joel
 */
public class YamlStreamConfiguration extends YamlConfiguration {

  private InputStream configInputStream;

  public YamlStreamConfiguration(InputStream configInputStream) throws FileNotFoundException {
    super(new Yaml().load(configInputStream));
    this.configInputStream = configInputStream;
  }

  public InputStream getConfigInputStream() {
    return configInputStream;
  }

  public void setConfigInputStream(InputStream configInputStream) {
    this.configInputStream = configInputStream;
  }

  public void saveConfigAsFile(File configFile) throws IOException {
    FileOutputStream fOS = new FileOutputStream(configFile);
    fOS.write(new Yaml().dumpAsMap(this.config).getBytes(StandardCharsets.UTF_8));
  }
}
