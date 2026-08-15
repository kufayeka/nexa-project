package nexa.framework.runtime.api.control;

import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;

public interface ConnectionControl {

        void enableConnection(String connectionId);

        void disableConnection(String connectionId);

        ConnectionInfo getConnectionInfo(String connectionId);

        void injectMessageIntoConnection(
                        String connectionId,
                        RuntimeMessage message);
}