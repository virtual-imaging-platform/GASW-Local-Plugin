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

import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswInput;
import fr.insalyon.creatis.gasw.plugin.ExecutorPlugin;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalMinorStatusServiceGenerator;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalMonitor;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalSubmit;
import java.util.ArrayList;
import java.util.List;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Silva
 */
@PluginImplementation
public class LocalExecutor implements ExecutorPlugin {

    private static final Logger logger = Logger.getLogger("fr.insalyon.creatis.gasw");

    private LocalSubmit localSubmit;

    @Override
    public String getName() {
        return LocalConstants.EXECUTOR_NAME;
    }

    @Override
    public void load(GaswInput gaswInput) throws GaswException {

        // fetch version from maven generated file
        logger.info("New Testing local GASW Plugin version "
                + getClass().getPackage().getImplementationVersion());

        LocalConfiguration.getInstance();
        localSubmit = new LocalSubmit(gaswInput, new LocalMinorStatusServiceGenerator());
    }

    @Override
    public List<Class> getPersistentClasses() throws GaswException {

        return new ArrayList<Class>();
    }

    @Override
    public String submit() throws GaswException {

        return localSubmit.submit();
    }

    @Override
    public void terminate() throws GaswException {

        LocalSubmit.terminate();
        LocalMonitor.getInstance().terminate();
    }
}
