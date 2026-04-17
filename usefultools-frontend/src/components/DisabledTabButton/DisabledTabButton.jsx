import React from 'react';
import styles from './DisabledTabButton.module.css';

/**
 * Tab button that appears disabled for guest users
 * Prevents clicking and shows visual disabled state
 */
const DisabledTabButton = ({ label, icon, title }) => {
  return (
    <button
      className={styles.disabledTab}
      disabled
      title={title || 'Please login to access this resource'}
    >
      {icon && <span className={styles.icon}>{icon}</span>}
      {label}
      <span className={styles.lockBadge}>🔒</span>
    </button>
  );
};

export default DisabledTabButton;
