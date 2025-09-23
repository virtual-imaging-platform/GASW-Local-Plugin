/* Copyright CNRS-CREATIS
 *
 * Rafael Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.rafaelsilva.com
 *
 * This software is a grid-enabled data-driven workflow manager and editor.
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw.plugin.executor.local;

import fr.insalyon.creatis.gasw.GaswConfiguration;
import fr.insalyon.creatis.gasw.GaswException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LocalConfiguration.class);
    private static LocalConfiguration instance;
    private int numberOfThreads;

    public static LocalConfiguration getInstance() throws GaswException {

        if (instance == null) {
            instance = new LocalConfiguration();
        }
        return instance;
    }

    private LocalConfiguration() throws GaswException {

        try {
            PropertiesConfiguration config = GaswConfiguration.getInstance().getPropertiesConfiguration();

            numberOfThreads = config.getInt(LocalConstants.LAB_NUMBER_OF_THREADS, 100);

            config.setProperty(LocalConstants.LAB_NUMBER_OF_THREADS, numberOfThreads);

            config.save();

        } catch (ConfigurationException ex) {
            logger.error("Error:", ex);
        }
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }
}
